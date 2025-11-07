#!/bin/bash

# Script para actualizar y desplegar el servicio (manual)

echo "Obteniendo cambios del repositorio..."
git pull

echo "Deteniendo el servicio anterior..."
docker compose down

echo "Construyendo la imagen Docker (sin cache)..."
docker compose build --no-cache

echo "Levantando el nuevo servicio..."
docker compose up -d

echo "Esperando a que el servicio esté listo..."
sleep 5

echo "Servicio actualizado y desplegado."