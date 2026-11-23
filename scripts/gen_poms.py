#!/usr/bin/env python3
"""Генерирует pom.xml gRPC-сервисов. Запуск из корня проекта."""
import sys

def dep(g, a, extra=""):
    return f"        <dependency>\n            <groupId>{g}</groupId>\n            <artifactId>{a}</artifactId>\n{extra}        </dependency>\n"

def pom(name, jpa=True, kafka=False, redis=True, mongo=False):
    deps = dep("com.creditscoring", "common")
    deps += dep("org.springframework.boot", "spring-boot-starter")
    if jpa:
        deps += dep("org.springframework.boot", "spring-boot-starter-data-jpa")
        deps += dep("org.postgresql", "postgresql", "            <scope>runtime</scope>\n")
    if mongo:
        deps += dep("org.springframework.boot", "spring-boot-starter-data-mongodb")
    if kafka:
        deps += dep("org.springframework.kafka", "spring-kafka")
    if redis:
        deps += dep("org.springframework.boot", "spring-boot-starter-data-redis")
    deps += dep("org.projectlombok", "lombok", "            <optional>true</optional>\n")
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.creditscoring</groupId>
        <artifactId>credit-scoring</artifactId>
        <version>0.1.0</version>
    </parent>
    <artifactId>{name}</artifactId>
    <name>{name}</name>

    <dependencies>
{deps}    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
"""

CONFIG = {
    "borrower-service": dict(),
    "statement-service": dict(redis=False, kafka=False),
    "application-service": dict(kafka=True),
    "analysis-service": dict(kafka=True),
    "scoring-service": dict(kafka=True),
    "notification-service": dict(jpa=False, mongo=True, kafka=True, redis=False),
}
for n, kw in CONFIG.items():
    open(f"{n}/pom.xml", "w", encoding="utf-8").write(pom(n, **kw))
print("poms ok")
