gcloud compute firewall-rules create bitbridge-io-rule \
    --direction=INGRESS \
    --priority=1000 \
    --network=default \
    --action=ALLOW \
    --rules=tcp:8080 \
    --source-ranges=0.0.0.0/0 \
    --target-tags=bitbridge-server


# 1. Interrogar directamente a las subredes de la red default para ver cuál está activa
export ALLOWED_REGION=$(gcloud compute networks subnets list --network=default --format="value(region)" --limit=1)

# 2. Asignar la zona 'b' (A veces la zona 'a' está llena en laboratorios muy concurridos, 'b' es más segura)
export GCP_ZONE="${ALLOWED_REGION}-b"
echo "🎯 Región detectada vía VPC Subnets: $ALLOWED_REGION"
echo "🎯 Zona de despliegue forzada: $GCP_ZONE"

# 3. Crear la instancia en la zona real autorizada
gcloud compute instances create bitbridge-host-hub1 \
    --zone="$GCP_ZONE" \
    --machine-type=e2-standard-2 \
    --image-family=rocky-linux-9 \
    --image-project=rocky-linux-cloud \
    --boot-disk-size=30GB \
    --boot-disk-type=pd-balanced \
    --tags=bitbridge-server

sudo dnf install git java-21-openjdk-devel -y

 git clone https://github.com/albrinBuzz/bitBridge.git

 cd bitBridge/


 git switch feat/network-nio-abstraction


 ./mvnw clean package

 cd target/

 java -jar target/BitBridge-CLI.jar --headless