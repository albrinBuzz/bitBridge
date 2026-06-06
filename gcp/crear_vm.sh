gcloud compute firewall-rules create bitbridge-io-rule \
    --direction=INGRESS \
    --priority=1000 \
    --network=default \
    --action=ALLOW \
    --rules=tcp:8080 \
    --source-ranges=0.0.0.0/0 \
    --target-tags=bitbridge-server


gcloud compute instances create bitbridge-host-hub \
    --machine-type=e2-standard-2 \
    --image-family=rocky-linux-9 \
    --image-project=rocky-linux-cloud \
    --boot-disk-size=30GB \
    --boot-disk-type=pd-balanced \
    --tags=bitbridge-server \
    --metadata=startup-script='#!/text/bash
    # Actualizar paquetes e instalar entorno de ejecución Java 21+
    sudo dnf update -y
    sudo dnf install java-21-openjdk-devel -y
    '