#!/bin/bash

# Script para actualizar y desplegar el servicio (manual)

echo "Obteniendo cambios del repositorio..."
git pull

echo "Construyendo la imagen Docker..."
docker compose build

echo "Deteniendo el servicio anterior..."
docker compose down

echo "Levantando el nuevo servicio..."
docker compose up -d

echo "Servicio actualizado y desplegado."