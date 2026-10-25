# Pixel 10 备用容量数值的写入来源追踪

2026-10-15。仅合成输入；诊断代码没有加入正式 APK。

## 结论与范围

硬件写监视点捕获到了与 Token spare 区相同的 `2048 / 268435456 / 256` 字节模式的实际写入者：Pixel 的 `/vendor/lib64/hw/mapper.pixel.so`，调用路径为 KV 更新锁定 AHardwareBuffer 时，获取、编码 graphics PlaneLayout 元数据。

这是数值模式来源的直接写指令证据，不是仅由数值猜测张量维度。之前用户截图对应的进程已经结束，无法恢复那次分配的完整历史。此次监视的是合成复现中选出的、带有该模式的已释放候选块；没有证明它就是此前截图的同一物理内存，也没有完成从每个原始截图槽位到最终写指令的一一关联。

独立的分配前/后实验已证明 Token spare 的此类非零字节在 vector 写入前存在。这里进一步证明相同的特征模式由 Pixel PlaneLayout 编码产生；并非所有非零 spare 数字都可以据此归为 PlaneLayout。

## 实际指令证据

候选块基址 P = `0x77337619a0`，进程 PID 22457。映射与寄存器均来自这次临时诊断运行。

| int32 方块偏移 | 捕获的 64 位 store 地址 | x9 实际写入值 | 按方块边界读出的 int32 | mapper ELF 中 store 偏移 |
|---|---|---|---|---|
| P+92 | P+93 (`0xb4000077337619fd`) | 8 | 2048 (`0x00000800`) | `0x2cc9c` |
| P+100 | P+101 (`0xb400007733761a05`) | 1048576 (`0x100000`, 1 MiB) | 268435456 (`0x10000000`) | `0x2ccec` |
| P+116 | P+117 (`0xb400007733761a15`) | 1 | 256 (`0x00000100`) | `0x2cd8c` |

三处指令均为 `str x9, [x10]`。写监视点在写后停住，PC 分别为 `0x758ba6fca0 / 0x758ba6fcf0 / 0x758ba6fd90`。ARM64 PAC 通过 XPACI 去除后解析地址。

例如 P+101 写入 `00 00 10 00 00 00 00 00`，P+100 起的四个字节则为 `00 00 00 10`。因此 268435456 是按错误边界/类型解释序列化元数据所得的显示值，而非驱动实际写入的 268435456。

## 捕获的调用栈

```
mapper.pixel.so + 0x2ccf0  (store 之后的 PC)
pixel::gralloc::metadata::get<(pixel::graphics::MetadataType)15>(...) + ...
pixel::gralloc::metadata::get(...)
pixel::gralloc::mapper::get(...)
android::hardware::graphics::mapper::implementation::Mapper::getStandardMetadata(...)
libui.so
android::Gralloc5Mapper::getPlaneLayouts(...)
android::GraphicBufferMapper::getPlaneLayouts(...)
android::GraphicBuffer::lockAsync(...)
libnativewindow.so
litert::internal::AhwbBuffer::Lock(...)
LiteRtTensorBufferT::Lock(...)
LiteRtLockTensorBuffer(...)
litert::TensorBufferScopedLock::Create(...)
litert::lm::HWKVCacheUpdate(...)
```

mapper ELF 的加载基址是 `0x758ba43000`。maps 中 RX 映射起点 `0x758ba57000`，文件偏移 `0x14000`。

读取到的系统二进制 SHA256：

- mapper.pixel.so: bb5b56eae6fafd6dd2f2f3dfd50fbaf6f5a5388eec272239a10278f4792527f5
- libui.so: ca8e9052968a1edb69ac7b7ba827584b45dd4451b34e7d842c93ea78215e8e01

## 为什么仅追 malloc/free 不够

临时运行时通过链接包装 malloc/calloc/realloc/free 和 C++ new/delete，保留了原始 std::vector<int>。它记录目标大小附近的分配，以及已知地址后续不同大小的分配、释放和字节。

记录显示地址被 TFLite op resolver、RoPE 运行时锁定 TensorBuffer 的临时哈希表、RE2、SentencePiece 和调试 JSON 等多个对象使用。调试读取自身也会改变后续堆复用。这些只是观察到的持有者，不应自动认作最终写入者。外部 libui / mapper 的分配不经过本运行时的链接包装，所以两条记录之间会有空档。

一轮仅靠内部追踪的诊断测试通过了非零 predecessor 匹配断言；其他轮（包括加硬件监视的最后一轮）该断言失败。这是覆盖范围不足的证据，不能写成完整分配追踪测试通过。硬件监视绕过了这一覆盖限制，捕获到外部 mapper 的实际 store，但仍受上述候选块关联范围限制。

## 重现和清理

`diagnostic-only.patch` 以已安装 Session 扩展、debug 和 DecodeNonSpeculative scrub 修复的运行时为基线。该补丁仅用于实验。构建额外带：

```
--per_file_copt=runtime/.*@-fno-omit-frame-pointer,-fno-optimize-sibling-calls
```

诊断测试源码保存在本目录，不在常规 androidTest 中。它会在合成运行中暂停候选进程，供设置硬件监视点。内部 malloc 追踪用固定内存表，线程栈边界缓存；释放后的测试读取用 process_vm_readv，无法读取时不报告字节，记录有并发/覆盖范围限制。

使用 NDK 的 aarch64 lldb-server，以 run-as 运行，并通过 Unix abstract socket 转发。LLDB 必须先 `platform select remote-android`；直接用主机默认 Darwin 平台会错误解释 Linux 信号。普通 APK没有 INTERNET 权限，所以没有给正式应用增加网络权限。

三个硬件监视点均已关闭，LLDB 已 detach，临时源码/链接选项、诊断测试、设备端 trace 文件及 lldb-server 均清理。正式 native 库逐字节恢复原备份，手机重新安装原正式 APK。正式 APK SHA256：309578c79bdf8803b5cd233bdcc9f679d8983756dc6a043d1480a55dcb6f7703。

恢复后 assembleDebug / assembleDebugAndroidTest / lintDebug 通过；MemoryInspectionDeviceTest 和 MultimodalDeviceTest：OK (2 tests), 7.991 s。
