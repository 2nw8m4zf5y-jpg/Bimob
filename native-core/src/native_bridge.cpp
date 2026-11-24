#include <jni.h>
#include "bimob/native_session.hpp"
#include <string>
namespace {std::string fromJString(JNIEnv*e,jstring s){if(!s)return {};const char*c=e->GetStringUTFChars(s,nullptr);if(!c)return {};std::string v(c);e->ReleaseStringUTFChars(s,c);return v;}jstring toJString(JNIEnv*e,const std::string&s){return e->NewStringUTF(s.c_str());}}
extern "C" JNIEXPORT jlong JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCreateSession(JNIEnv*,jobject){try{return static_cast<jlong>(bimob::bridge::createSession());}catch(...){return 0;}}
extern "C" JNIEXPORT void JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeDestroySession(JNIEnv*,jobject,jlong h){bimob::bridge::destroySession(static_cast<std::uint64_t>(h));}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeDispatchCommandJson(JNIEnv*e,jobject,jlong h,jstring json){return toJString(e,bimob::bridge::dispatchCommandJson(static_cast<std::uint64_t>(h),fromJString(e,json)));}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeImportCanonicalSnapshot(JNIEnv*e,jobject,jlong h,jstring json){return toJString(e,bimob::bridge::importCanonicalSnapshot(static_cast<std::uint64_t>(h),fromJString(e,json)));}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeExportCanonicalSnapshot(JNIEnv*e,jobject,jlong h){return toJString(e,bimob::bridge::exportCanonicalSnapshot(static_cast<std::uint64_t>(h)));}
extern "C" JNIEXPORT jboolean JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeBeginGesture(JNIEnv*e,jobject,jlong h,jstring id){return bimob::bridge::beginGesture(static_cast<std::uint64_t>(h),fromJString(e,id));}
extern "C" JNIEXPORT jboolean JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCommitGesture(JNIEnv*e,jobject,jlong h,jstring id){return bimob::bridge::commitGesture(static_cast<std::uint64_t>(h),fromJString(e,id));}
extern "C" JNIEXPORT jboolean JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCancelGesture(JNIEnv*e,jobject,jlong h,jstring id){return bimob::bridge::cancelGesture(static_cast<std::uint64_t>(h),fromJString(e,id));}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeExportRenderGeometry(JNIEnv*e,jobject,jlong h){
 try{auto bytes=bimob::bridge::exportRenderGeometry(static_cast<std::uint64_t>(h));if(bytes.empty())return e->NewByteArray(0);auto out=e->NewByteArray(static_cast<jsize>(bytes.size()));if(out)e->SetByteArrayRegion(out,0,static_cast<jsize>(bytes.size()),reinterpret_cast<const jbyte*>(bytes.data()));return out;}catch(...){return e->NewByteArray(0);}
}
