#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "c/engine.h"
#include "c/error_reporter.h"
typedef struct { LiteRtLmEngine* engine; LiteRtLmSession* session; int gemma3; } Model;
static void fail(JNIEnv* env,const char* fallback){const char* error=litert_lm_get_last_error_message();(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalStateException"),error?error:fallback);}
static LiteRtLmSession* create_session(Model* m){
 LiteRtLmSessionConfig* config=litert_lm_session_config_create();
 if(!config)return NULL;
 litert_lm_session_config_set_max_output_tokens(config,512);
 // The app supplies only the current Gemma turn; no Conversation/history list.
 // Session adds BOS once and retains its KV cache between GenerateContent calls.
 litert_lm_session_config_set_apply_prompt_template(config,false);
 LiteRtLmSession* result=litert_lm_engine_create_session(m->engine,config);
 litert_lm_session_config_delete(config);
 return result;
}
JNIEXPORT jlong JNICALL Java_com_example_annoymous_NativeModel_openWithBackend(JNIEnv* env,jclass cls,jstring model,jstring cache,jstring libraries,jstring backend,jboolean scrub_token_ids){
 const char* path=(*env)->GetStringUTFChars(env,model,0);const char* dir=(*env)->GetStringUTFChars(env,cache,0);const char* libs=(*env)->GetStringUTFChars(env,libraries,0);
 const char* backend_name=(*env)->GetStringUTFChars(env,backend,0);
 int is_npu=strcmp(backend_name,"npu")==0;
 int gemma3=strstr(path,"Gemma3-1B-IT_q8_")!=NULL;
 if(!is_npu&&scrub_token_ids==JNI_TRUE){
  (*env)->ReleaseStringUTFChars(env,model,path);(*env)->ReleaseStringUTFChars(env,cache,dir);(*env)->ReleaseStringUTFChars(env,libraries,libs);(*env)->ReleaseStringUTFChars(env,backend,backend_name);
  (*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalArgumentException"),"Token ID scrubbing requires the NPU backend.");return 0;
 }
 LiteRtLmEngineSettings* s=litert_lm_engine_settings_create(path,backend_name,gemma3?NULL:"cpu",gemma3?NULL:"cpu");
 (*env)->ReleaseStringUTFChars(env,backend,backend_name);
 (*env)->ReleaseStringUTFChars(env,model,path);
 if(!s){(*env)->ReleaseStringUTFChars(env,cache,dir);(*env)->ReleaseStringUTFChars(env,libraries,libs);fail(env,"Could not create model settings.");return 0;}
 if(is_npu&&litert_lm_engine_settings_set_npu_token_id_scrubbing(s,scrub_token_ids==JNI_TRUE)!=0){(*env)->ReleaseStringUTFChars(env,cache,dir);(*env)->ReleaseStringUTFChars(env,libraries,libs);litert_lm_engine_settings_delete(s);fail(env,"Could not configure NPU token-ID scrubbing.");return 0;}
 litert_lm_engine_settings_set_cache_dir(s,dir);litert_lm_engine_settings_set_litert_dispatch_lib_dir(s,libs);litert_lm_engine_settings_set_max_num_tokens(s,gemma3?1280:4096);
 (*env)->ReleaseStringUTFChars(env,cache,dir);(*env)->ReleaseStringUTFChars(env,libraries,libs);
 Model* m=calloc(1,sizeof(Model));if(!m){litert_lm_engine_settings_delete(s);fail(env,"Not enough memory.");return 0;}
 m->gemma3=gemma3;
 m->engine=litert_lm_engine_create(s);litert_lm_engine_settings_delete(s);
 if(!m->engine){free(m);fail(env,"Model initialization failed.");return 0;}
 m->session=create_session(m);if(!m->session){fail(env,"Could not create the session.");litert_lm_engine_delete(m->engine);free(m);return 0;}return (jlong)(intptr_t)m;
}
JNIEXPORT jbyteArray JNICALL Java_com_example_annoymous_NativeModel_send(JNIEnv* env,jclass cls,jlong handle,jbyteArray prompt){
 Model* m=(Model*)(intptr_t)handle;if(!m||!m->session){fail(env,"Load the model or start a new session first.");return NULL;}
 jsize n=(*env)->GetArrayLength(env,prompt);
 char* text=calloc((size_t)n+1,1);if(!text){fail(env,"Not enough memory.");return NULL;}
 (*env)->GetByteArrayRegion(env,prompt,0,n,(jbyte*)text);
 if((*env)->ExceptionCheck(env)){free(text);return NULL;}
 LiteRtLmInputData* input=litert_lm_input_data_create(kLiteRtLmInputDataTypeText,text,(size_t)n);
 memset(text,0,(size_t)n);free(text);
 if(!input){fail(env,"Could not prepare the message.");return NULL;}
 const LiteRtLmInputData* inputs[]={input};
 // Reuse the same Session/KV, providing only the newest turn.
 LiteRtLmResponses* response=litert_lm_session_generate_content(m->session,inputs,1);
 litert_lm_input_data_delete(input);
 if(!response){fail(env,"Generation failed. Start a new session to retry.");return NULL;}
 const char* raw=litert_lm_responses_get_response_text_at(response,0);
 jbyteArray out=NULL;
 if(raw){size_t len=strlen(raw);out=(*env)->NewByteArray(env,(jsize)len);if(out)(*env)->SetByteArrayRegion(env,out,0,(jsize)len,(const jbyte*)raw);}else fail(env,"The model returned no text.");
 litert_lm_responses_delete(response);
 return out;
}
JNIEXPORT void JNICALL Java_com_example_annoymous_NativeModel_reset(JNIEnv* env,jclass cls,jlong handle){
 Model* m=(Model*)(intptr_t)handle;if(!m)return;
 if(m->session)litert_lm_session_delete(m->session);
 m->session=create_session(m);if(!m->session)fail(env,"Could not start a new session.");
}
JNIEXPORT void JNICALL Java_com_example_annoymous_NativeModel_close(JNIEnv* env,jclass cls,jlong handle){
 Model* m=(Model*)(intptr_t)handle;if(!m)return;
 if(m->session)litert_lm_session_delete(m->session);
 litert_lm_engine_delete(m->engine);free(m);
}

extern LiteRtLmResponses* ghost_session_generate_multimodal(LiteRtLmEngine*,LiteRtLmSession*,const char*,const char*,const char*);
JNIEXPORT jbyteArray JNICALL Java_com_example_annoymous_NativeModel_sendMedia(JNIEnv* env,jclass cls,jlong handle,jbyteArray message,jstring image,jstring audio){
 Model* m=(Model*)(intptr_t)handle;if(!m||!m->session){fail(env,"Load the model first.");return NULL;}
 if(m->gemma3){
  if((*env)->GetStringLength(env,image)||(*env)->GetStringLength(env,audio)){
   (*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalArgumentException"),"Gemma 3 1B supports text only.");return NULL;
  }
  const char* prefix="<start_of_turn>user\n";const char* suffix="<end_of_turn>\n<start_of_turn>model\n";
  jsize n=(*env)->GetArrayLength(env,message),a=(jsize)strlen(prefix),b=(jsize)strlen(suffix);
  jbyteArray turn=(*env)->NewByteArray(env,a+n+b);if(!turn)return NULL;
  (*env)->SetByteArrayRegion(env,turn,0,a,(const jbyte*)prefix);
  jbyte* bytes=(*env)->GetByteArrayElements(env,message,NULL);if(!bytes){(*env)->DeleteLocalRef(env,turn);return NULL;}
  (*env)->SetByteArrayRegion(env,turn,a,n,bytes);(*env)->ReleaseByteArrayElements(env,message,bytes,JNI_ABORT);
  (*env)->SetByteArrayRegion(env,turn,a+n,b,(const jbyte*)suffix);
  jbyteArray result=Java_com_example_annoymous_NativeModel_send(env,cls,handle,turn);
  // Release the temporary formatted current turn; no conversation replay.
  jbyte* data=(*env)->GetByteArrayElements(env,turn,NULL);if(data){memset(data,0,(size_t)(a+n+b));(*env)->ReleaseByteArrayElements(env,turn,data,0);}
  (*env)->DeleteLocalRef(env,turn);return result;
 }
 jsize n=(*env)->GetArrayLength(env,message);char* text=calloc((size_t)n+1,1);if(!text){fail(env,"Not enough memory.");return NULL;}
 (*env)->GetByteArrayRegion(env,message,0,n,(jbyte*)text);
 if((*env)->ExceptionCheck(env)){free(text);return NULL;}
 const char* image_path=(*env)->GetStringUTFChars(env,image,0);
 const char* audio_path=(*env)->GetStringUTFChars(env,audio,0);
 LiteRtLmResponses* response=ghost_session_generate_multimodal(m->engine,m->session,text,image_path,audio_path);
 (*env)->ReleaseStringUTFChars(env,image,image_path);(*env)->ReleaseStringUTFChars(env,audio,audio_path);memset(text,0,(size_t)n);free(text);
 if(!response){fail(env,"Multimodal generation failed.");return NULL;}
 const char* raw=litert_lm_responses_get_response_text_at(response,0);jbyteArray out=NULL;
 if(raw){size_t len=strlen(raw);out=(*env)->NewByteArray(env,(jsize)len);if(out)(*env)->SetByteArrayRegion(env,out,0,(jsize)len,(const jbyte*)raw);}else fail(env,"The model returned no text.");
 litert_lm_responses_delete(response);return out;
}

extern LiteRtLmResponses* ghost_session_debug(LiteRtLmSession*,const char*);
JNIEXPORT jbyteArray JNICALL Java_com_example_annoymous_NativeModel_inspect(JNIEnv* env,jclass cls,jlong handle,jstring query){
 Model* m=(Model*)(intptr_t)handle;if(!m||!m->session){fail(env,"No active session.");return NULL;}
 const char* request=(*env)->GetStringUTFChars(env,query,0);if(!request)return NULL;
 LiteRtLmResponses* response=ghost_session_debug(m->session,request);
 (*env)->ReleaseStringUTFChars(env,query,request);
 if(!response){fail(env,"Memory inspection failed.");return NULL;}
 const char* raw=litert_lm_responses_get_response_text_at(response,0);jbyteArray out=NULL;
 if(raw){size_t len=strlen(raw);out=(*env)->NewByteArray(env,(jsize)len);if(out)(*env)->SetByteArrayRegion(env,out,0,(jsize)len,(const jbyte*)raw);}
 litert_lm_responses_delete(response);return out;
}

// Read our own address space through the kernel, never dereference a freed pointer.
#include <sys/uio.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>
JNIEXPORT jbyteArray JNICALL Java_com_example_annoymous_NativeModel_readAddress(JNIEnv* env,jclass cls,jlong address,jint count){
 if(address==0||count<=0||count>4096){(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalArgumentException"),"Invalid address range.");return NULL;}
 unsigned char bytes[4096];struct iovec local={bytes,(size_t)count},remote={(void*)((uintptr_t)address & UINT64_C(0x00ffffffffffffff)),(size_t)count};
 ssize_t n=syscall(__NR_process_vm_readv,getpid(),&local,1,&remote,1,0);
 if(n<0){(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalStateException"),strerror(errno));return NULL;}
 jbyteArray result=(*env)->NewByteArray(env,(jsize)n);if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)n,(jbyte*)bytes);memset(bytes,0,sizeof(bytes));return result;
}
