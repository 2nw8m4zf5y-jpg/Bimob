#include <jni.h>
#include "bimob/native_session.hpp"
#include <string>
#include <exception>
#include <cstdint>
namespace {
std::string fromJString(JNIEnv* e,jstring s){
 if(!s)return {};
 const char* c=e->GetStringUTFChars(s,nullptr);
 if(!c)return {};
 std::string v(c);e->ReleaseStringUTFChars(s,c);return v;
}
jstring toJString(JNIEnv* e,const std::string& s){return e->NewStringUTF(s.c_str());}
jstring safeError(JNIEnv* e,const char* message){return toJString(e,std::string("{\"ok\":false,\"error\":\"")+message+"\"}");}
}
extern "C" JNIEXPORT jlong JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCreateSession(JNIEnv* e,jobject){
 try{return static_cast<jlong>(bimob::bridge::createSession());}catch(...){return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeDestroySession(JNIEnv*,jobject,jlong h){
 try{(void)bimob::bridge::destroySession(static_cast<std::uint64_t>(h));}catch(...){}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeDispatchCommandJson(JNIEnv* e,jobject,jlong h,jstring json){
 try{return toJString(e,bimob::bridge::dispatchCommandJson(static_cast<std::uint64_t>(h),fromJString(e,json)));}
 catch(const std::exception&){return safeError(e,"Native command failed safely");}
 catch(...){return safeError(e,"Unknown native command failure");}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeImportCanonicalSnapshot(JNIEnv* e,jobject,jlong h,jstring json){
 try{return toJString(e,bimob::bridge::importCanonicalSnapshot(static_cast<std::uint64_t>(h),fromJString(e,json)));}
 catch(const std::exception&){return safeError(e,"Native snapshot import failed safely");}
 catch(...){return safeError(e,"Unknown native snapshot import failure");}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeExportCanonicalSnapshot(JNIEnv* e,jobject,jlong h){
 try{return toJString(e,bimob::bridge::exportCanonicalSnapshot(static_cast<std::uint64_t>(h)));}
 catch(const std::exception&){return safeError(e,"Native snapshot export failed safely");}
 catch(...){return safeError(e,"Unknown native snapshot export failure");}
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeBeginGesture(JNIEnv* e,jobject,jlong h,jstring id){
 try{return bimob::bridge::beginGesture(static_cast<std::uint64_t>(h),fromJString(e,id));}catch(...){return JNI_FALSE;}
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCommitGesture(JNIEnv* e,jobject,jlong h,jstring id){
 try{return bimob::bridge::commitGesture(static_cast<std::uint64_t>(h),fromJString(e,id));}catch(...){return JNI_FALSE;}
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCancelGesture(JNIEnv* e,jobject,jlong h,jstring id){
 try{return bimob::bridge::cancelGesture(static_cast<std::uint64_t>(h),fromJString(e,id));}catch(...){return JNI_FALSE;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeExportRenderGeometry(JNIEnv* e,jobject,jlong h){
 try{
  auto bytes=bimob::bridge::exportRenderGeometry(static_cast<std::uint64_t>(h));
  if(bytes.empty())return e->NewByteArray(0);
  auto out=e->NewByteArray(static_cast<jsize>(bytes.size()));
  if(out)e->SetByteArrayRegion(out,0,static_cast<jsize>(bytes.size()),reinterpret_cast<const jbyte*>(bytes.data()));
  return out;
 }catch(...){return e->NewByteArray(0);}
}
