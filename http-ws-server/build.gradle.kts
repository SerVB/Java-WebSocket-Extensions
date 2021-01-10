plugins {
    java
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.1")
}
