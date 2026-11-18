# =====================================================================
# Etapa 1: Compilación con Maven y JDK 17
# =====================================================================
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copiar descriptores Maven para aprovechar caché de capas
COPY pom.xml ./
COPY common/pom.xml ./common/pom.xml
COPY forum-service/pom.xml ./forum-service/pom.xml

RUN mvn dependency:go-offline -B

# Copiar código fuente de los módulos
COPY common/src ./common/src
COPY forum-service/src ./forum-service/src

# Empaquetar el microservicio omitiendo pruebas en el build de imagen
RUN mvn clean package -DskipTests -B

# =====================================================================
# Etapa 2: Ejecución ligera con Eclipse Temurin JRE 17
# =====================================================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Directorio opcional para montar Wallet de Oracle Cloud Autonomous Database
RUN mkdir -p /app/wallet

COPY --from=build /workspace/forum-service/target/forum-service-1.0.0-SNAPSHOT.jar /app/app.jar

ENV FORUM_PORT=8082 \
    FORUM_DB_URL="jdbc:oracle:thin:@host.docker.internal:1521/XEPDB1" \
    FORUM_DB_USERNAME="FORO_FORUM" \
    FORUM_DB_PASSWORD="forum2026" \
    JWT_SECRET="clave_secreta_jwt_para_tokens_de_al_menos_32_bytes_12345" \
    DEMO_USERS_URL="http://users-service:8081" \
    DEMO_PASSWORD="Demo1234" \
    SECURITY_CORS_ORIGINS="http://localhost:4200,http://localhost:3000" \
    JAVA_OPTS=""

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
