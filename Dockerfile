FROM eclipse-temurin:17-jre-alpine

# Metadata
LABEL maintainer="enterprise-architecture@company.com"
LABEL description="SOAP Gateway - Bridge SOAP to REST"
LABEL version="1.0.0"

# Variables de entorno
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENV SPRING_PROFILES_ACTIVE=prod

# Crear usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Directorio de trabajo
WORKDIR /app

# Copiar JAR
COPY target/soap-gateway-1.0.0.jar app.jar

# Copiar configuración
COPY src/main/resources/application.yml application.yml
COPY src/main/resources/bridge-protocols.yml bridge-protocols.yml

# Cambiar permisos
RUN chown -R appuser:appgroup /app

# Cambiar a usuario no-root
USER appuser

# Exponer puerto
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
