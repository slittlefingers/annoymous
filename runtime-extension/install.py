"""Install the app-specific Session preprocessing entry point into a checkout."""
import argparse
from pathlib import Path
import shutil
parser=argparse.ArgumentParser()
parser.add_argument('checkout',type=Path)
args=parser.parse_args()
source=Path(__file__).resolve().parent/'ghost_multimodal.cc'
shutil.copy2(source,args.checkout/'c/ghost_multimodal.cc')
p=args.checkout/'c/BUILD'
s=p.read_text();a=s.index('    name = "litert-lm",');tail=s[a:]
if '"ghost_multimodal.cc"' not in tail:
 tail=tail.replace('    srcs = [','    srcs = [\n        "ghost_multimodal.cc",',1)
for dependency in ['//runtime/conversation/model_data_processor:model_data_processor_factory','//runtime/conversation/model_data_processor:model_data_processor']:
 if '"'+dependency+'"' not in tail:
  tail=tail.replace('    deps = ENGINE_COMMON_DEPS + [','    deps = ENGINE_COMMON_DEPS + [\n        "'+dependency+'",',1)
p.write_text(s[:a]+tail)
print('Runtime extension installed. Build //c:litert-lm using the same LiteRT revision as the dispatch library.')
