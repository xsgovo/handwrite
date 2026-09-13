# protobuf-javalite 不自带 consumer 规则：Lite 运行时构建 MessageSchema 时
# 会按名称反射读取生成类的字段，字段被 R8 重命名后启动即崩溃
# （NoSuchFieldException: No field xxx_ in class ...)。
# 官方要求：https://github.com/protocolbuffers/protobuf/blob/main/java/lite.md
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
