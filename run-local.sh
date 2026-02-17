#!/bin/bash

# Local profile (no config server)
echo "Starting app with LOCAL profile..."
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
