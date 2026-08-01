# ===== 构建阶段: 使用 JDK 编译并打包 =====
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY src ./src
RUN mkdir -p build/classes \
    && javac --release 17 -encoding UTF-8 -d build/classes src/com/wolweb/*.java \
    && jar cfe build/wolweb.jar com.wolweb.Main -C build/classes .

# ===== 运行阶段: 仅 JRE, 镜像更小 =====
FROM eclipse-temurin:17-jre
WORKDIR /data
COPY --from=build /src/build/wolweb.jar /opt/wolweb/wolweb.jar
EXPOSE 9999
# 配置文件 wolweb.properties 生成在 /data, 建议挂载数据卷持久化
VOLUME ["/data"]
CMD ["java", "-jar", "/opt/wolweb/wolweb.jar"]