#!/bin/bash
# Deploy Chat Server lên Google Cloud
# Chỉnh sửa các biến dưới đây trước khi chạy

# ==================== CẤU HÌNH ====================
PROJECT_ID="your-project-id"              # Thay bằng Google Cloud Project ID
ZONE="asia-southeast1-a"                   # Zone (có thể thay: us-central1-a, ...)
INSTANCE_NAME="chat-server"                # Tên VM instance
MACHINE_TYPE="e2-micro"                    # Loại máy (miễn phí)
IMAGE_FAMILY="ubuntu-2204-lts"             # OS
SERVER_PORT="5000"                         # Port server

# ==================== BƯỚC 1: TẠO VM ====================
echo "📦 Tạo Compute Engine instance..."
gcloud compute instances create $INSTANCE_NAME \
  --project=$PROJECT_ID \
  --zone=$ZONE \
  --machine-type=$MACHINE_TYPE \
  --image-family=$IMAGE_FAMILY \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=20GB

# ==================== BƯỚC 2: TẠO FIREWALL RULE ====================
echo "🔥 Tạo Firewall rule cho port $SERVER_PORT..."
gcloud compute firewall-rules create allow-chat \
  --project=$PROJECT_ID \
  --allow=tcp:$SERVER_PORT \
  --source-ranges=0.0.0.0/0

# ==================== BƯỚC 3: LẤY EXTERNAL IP ====================
echo "📍 Lấy External IP..."
EXTERNAL_IP=$(gcloud compute instances describe $INSTANCE_NAME \
  --project=$PROJECT_ID \
  --zone=$ZONE \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)')

echo "✅ VM sẵn sàng!"
echo "   Tên: $INSTANCE_NAME"
echo "   IP: $EXTERNAL_IP"
echo "   Zone: $ZONE"
echo ""
echo "📝 Bước tiếp theo:"
echo "   1. Chạy: gcloud compute ssh $INSTANCE_NAME --zone=$ZONE"
echo "   2. Cài Java: sudo apt install openjdk-17-jdk -y"
echo "   3. Tải JAR: gcloud compute scp dist/ChatApp-TCP.jar $INSTANCE_NAME:/home/\$USER/app.jar --zone=$ZONE"
echo "   4. Chạy: java -jar app.jar"
