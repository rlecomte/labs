#!/bin/bash

docker rm -f store-postgres;
docker run --name store-postgres -p 5434:5432 -e POSTGRES_PASSWORD=trololo -v /home/rlecomte/workspace/cats-test/postgres/init:/docker-entrypoint-initdb.d -d postgres;

#pgcli -h localhost -p 5434 -U postgres -d postgres
