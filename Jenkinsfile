pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        IMAGE_TAG = "${env.BUILD_NUMBER}"    // version tag
        S3_BUCKET = 'ecommerce-project-artifacts-032f73c4'
    }

    stages {

        /* ------------------- Checkout ------------------- */
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        /* ------------------- Backend Build ------------------- */
        stage('Build Backend (Maven)') {
            steps {
                dir('Ecommerce-Backend') {
                    echo "🏗️ Building backend with Maven"
                    sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                }
            }
        }

        /* ------------------- Frontend Build ------------------- */
        stage('Build Frontend (npm)') {
            steps {
                dir('Ecommerce-Frontend') {
                    echo "🎨 Building frontend"
                    sh 'npm install && npm run build'
                }
            }
        }

        /* ------------------- Upload Artifacts to S3 ------------------- */
        stage('Upload Build Artifacts to S3') {
            steps {
                script {
                    echo "📦 Uploading build artifacts to S3 bucket: ${S3_BUCKET}"

                    // Upload backend JAR
                    sh """
                    aws s3 cp Ecommerce-Backend/target/ecommerce-backend.jar \
                    s3://${S3_BUCKET}/backend/ecommerce-backend-${IMAGE_TAG}.jar
                    """

                    // Upload frontend build folder
                    sh """
                    aws s3 sync Ecommerce-Frontend/build/ \
                    s3://${S3_BUCKET}/frontend/${IMAGE_TAG}/ --delete
                    """

                    echo "✅ Artifacts uploaded successfully to S3!"
                }
            }
        }

        /* ------------------- AWS Account ID ------------------- */
        stage('Get AWS Account ID') {
            steps {
                script {
                    env.AWS_ACCOUNT_ID = sh(
                        script: "aws sts get-caller-identity --query Account --output text",
                        returnStdout: true
                    ).trim()
                    echo "Deploying with Account ID: ${env.AWS_ACCOUNT_ID}"
                }
            }
        }

        /* ------------------- Docker Build ------------------- */
        stage('Build Docker Images') {
            steps {
                script {
                    def BACKEND_ECR  = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    // 🧱 Build both backend and frontend with 2 tags (latest + build number)
                    sh """
                    docker build -t ${BACKEND_ECR}:${IMAGE_TAG} -t ${BACKEND_ECR}:latest Ecommerce-Backend/
                    docker build -t ${FRONTEND_ECR}:${IMAGE_TAG} -t ${FRONTEND_ECR}:latest Ecommerce-Frontend/
                    """
                }
            }
        }

        /* ------------------- Trivy Scan ------------------- */
        stage('Scan Docker Images with Trivy') {
            steps {
                script {
                    echo "🔍 Scanning Docker images using Trivy..."

                    def BACKEND_ECR  = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh """
                    trivy image --format table --output trivy-backend-report.txt ${BACKEND_ECR}:${IMAGE_TAG}
                    trivy image --format table --output trivy-frontend-report.txt ${FRONTEND_ECR}:${IMAGE_TAG}
                    """

                    archiveArtifacts artifacts: 'trivy-*-report.txt', allowEmptyArchive: true
                }
            }
        }

        /* ------------------- ECR Login ------------------- */
        stage('Login to ECR') {
            steps {
                script {
                    def ECR_REGISTRY = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                }
            }
        }

        /* ------------------- Push to ECR ------------------- */
        stage('Push Docker Images') {
            steps {
                script {
                    def BACKEND_ECR  = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    // 🟢 Push both latest and versioned tags
                    sh """
                    docker push ${BACKEND_ECR}:${IMAGE_TAG}
                    docker push ${BACKEND_ECR}:latest
                    docker push ${FRONTEND_ECR}:${IMAGE_TAG}
                    docker push ${FRONTEND_ECR}:latest
                    """

                    // Optional cleanup
                    sh "docker rmi ${BACKEND_ECR}:${IMAGE_TAG} ${BACKEND_ECR}:latest || true"
                    sh "docker rmi ${FRONTEND_ECR}:${IMAGE_TAG} ${FRONTEND_ECR}:latest || true"
                }
            }
        }

        /* ------------------- ECS Deploy ------------------- */
        stage('Deploy to ECS') {
            steps {
                sh """
                aws ecs update-service \
                    --cluster ecommerce-project-cluster \
                    --service ecommerce-project-backend-service \
                    --force-new-deployment \
                    --region ${AWS_REGION}
                """

                sh """
                aws ecs update-service \
                    --cluster ecommerce-project-cluster \
                    --service ecommerce-project-frontend-service \
                    --force-new-deployment \
                    --region ${AWS_REGION}
                """
            }
        }
    }

    post {
        success { echo "✅ Deployment successful! ECS will pull latest images." }
        failure { echo "❌ Deployment failed. Check logs, Trivy reports, or S3 upload stage." }
    }
}
