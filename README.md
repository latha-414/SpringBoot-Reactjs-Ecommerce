Deploying an E-Commerce App to AWS ECS using Jenkins, Docker & Terraform

 🧩 Overview

By the end of this project, you’ll have a complete CI/CD pipeline that:
✅ Builds a Docker image of the app
✅ Scans it with Trivy for vulnerabilities
✅ Pushes the image to AWS Elastic Container Registry (ECR)
✅ Deploys the containerized app to ECS Fargate using Terraform


## 🎯 Project Goals

✅ Develop and containerize the E-Commerce app
✅ Use Jenkins for CI/CD automation
✅ Use Terraform to provision AWS infrastructure
✅ Scan Docker images for vulnerabilities before deployment
✅ Monitor logs using CloudWatch


## 📂 Project Structure

Ecommerce-DevOps-Project/
│
├── Ecommerce-Backend/
│   ├── src/
│   ├── target/
│   ├── Dockerfile
│   ├── pom.xml
│   └── mvnw, mvnw.cmd
│
├── Ecommerce-Frontend/
│   ├── public/
│   ├── src/
│   ├── Dockerfile
│   ├── package.json
│   └── vite.config.js
│
├── terraform/         
├── Jenkinsfile        
└── README.md          


## 🧠 Tools & Technologies

| Tool                  | Purpose                                |
| --------------------- | -------------------------------------- |
| **AWS ECS (Fargate)** | Host and run containerized app         |
| **ECR**               | Store Docker images                    |
| **Terraform**         | Provision AWS infrastructure           |
| **Jenkins**           | Automate build, test, deploy pipeline  |
| **Docker**            | Containerize the application           |
| **Trivy**             | Scan Docker images for vulnerabilities |
| **CloudWatch**        | Log and monitor ECS tasks              |

---

## ⚙️ Jenkins Server Setup

Run these commands on your Jenkins EC2 server to install all necessary tools:

### 🐳 Install Docker

sudo apt update
sudo apt install -y docker.io
sudo systemctl start docker
sudo systemctl enable docker
docker --version
sudo usermod -aG docker jenkins
groups jenkins

### ☁️ Install AWS CLI

sudo apt update
sudo apt install -y unzip curl python3 python3-pip
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
aws --version

### ⚡ Install Node.js & npm

sudo apt update
sudo apt install nodejs npm -y
node -v
npm -v

### ☕ Install Maven

sudo apt update
sudo apt install maven -y
mvn --version

### 🔒 Install Trivy (Security Scanner)

sudo apt update && \
sudo apt install wget apt-transport-https gnupg lsb-release -y && \
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add - && \
echo "deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/trivy.list && \
sudo apt update && \
sudo apt install trivy -y

## 🔐 IAM Roles & Policies for Jenkins

Attach the following policies to the **Jenkins IAM Role** (or user):

| Policy Name                             | Type        | Purpose                                    |
| --------------------------------------- | ----------- | ------------------------------------------ |
| **AmazonEC2ContainerRegistryPowerUser** | AWS Managed | Push/Pull Docker images to ECR             |
| **AmazonECS_FullAccess**                | AWS Managed | Deploy and manage ECS services             |
| **AmazonS3FullAccess**                  | AWS Managed | Store Terraform state files                |
| **CloudWatchLogsFullAccess**            | AWS Managed | View and manage ECS logs                   |
| **s3full**                              | Inline      | Custom S3 permissions for specific buckets |


## 🧱 Terraform Configuration (Example)

provider "aws" {
  region = "us-east-1"
}

resource "aws_ecr_repository" "app" {
  name = "ecommerce-app"
}

resource "aws_ecs_cluster" "main" {
  name = "ecommerce-cluster"
}

Run Terraform commands:

terraform init
terraform apply -auto-approve

## 🔄 Jenkins Pipeline (CI/CD Flow)

1️⃣ **Checkout Code from GitHub**
2️⃣ **Build Docker Image**
3️⃣ **Run Trivy Scan**
4️⃣ **Push Image to AWS ECR**
5️⃣ **Deploy to ECS using Terraform**
6️⃣ **Monitor logs in CloudWatch**


## 📊 Monitoring Deployment

Check:

* Jenkins Console Output → for build and deploy logs
* AWS ECS Console → for service and task status
* AWS CloudWatch → for container logs

## ✅ Final Testing

Once deployment completes, test your ECS service endpoint:

curl http://your-ecs-service-url
