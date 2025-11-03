pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        S3_BUCKET = 'ecommerce-project-artifacts-032f73c4'  // ✅ Use your real bucket name here
    }

    stages {

        /* ------------------- 1. Checkout Code ------------------- */
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        /* ------------------- 2. Build Backend ------------------- */
        stage('Build Backend (Maven)') {
            steps {
                dir('Ecommerce-Backend') {
                    echo "🚧 Building Backend with Maven..."
                    sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                }
            }
        }

        /* ------------------- 3. Build Frontend ------------------- */
        stage('Build Frontend (npm)') {
            steps {
                dir('Ecommerce-Frontend') {
                    echo "🧱 Building Frontend with npm..."
                    sh 'npm install && npm run build'
                }
            }
        }

        /* ------------------- 4. Get AWS Account ID ------------------- */
        stage('Get AWS Account ID') {
            steps {
                script {
                    env.AWS_ACCOUNT_ID = sh(
                        script: "aws sts get-caller-identity --query Account --output text",
                        returnStdout: true
                    ).trim()
                    echo "AWS Account ID: ${env.AWS_ACCOUNT_ID}"
                }
            }
        }

        /* ------------------- 5. Build Docker Images ------------------- */
        stage('Build Docker Images') {
            steps {
                script {
                    def BACKEND_ECR  = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    echo "🐳 Building Docker images..."
                    sh """
                    docker build -t ${BACKEND_ECR}:${IMAGE_TAG} -t ${BACKEND_ECR}:latest Ecommerce-Backend/
                    docker build -t ${FRONTEND_ECR}:${IMAGE_TAG} -t ${FRONTEND_ECR}:latest Ecommerce-Frontend/
                    """
                }
            }
        }

        /* ------------------- 6. Scan Docker Images (Trivy) ------------------- */
        stage('Scan Docker Images with Trivy') {
            steps {
                script {
                    echo "🔍 Scanning Docker images using Trivy..."
                    def BACKEND_ECR  = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh """
                    trivy image --format table --output trivy-backend-report.txt ${BACKEND_ECR}:${IMAGE_TAG}
                    trivy image --format table --output trivy-frontend-report.txt ${FRONTEND_ECR}:${IMAGE_TAG}
                    """

                    archiveArtifacts artifacts: 'trivy-*-report.txt', allowEmptyArchive: true
                }
            }
        }

        /* ------------------- 7. ECR Login ------------------- */
        stage('Login to ECR') {
            steps {
                script {
                    echo "🔑 Logging in to ECR..."
                    def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                }
            }
        }

        /* ------------------- 8. Push Docker Images ------------------- */
        stage('Push Docker Images') {
            steps {
                script {
                    echo "📤 Pushing Docker images to ECR..."
                    def BACKEND_ECR  = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh """
                    docker push ${BACKEND_ECR}:${IMAGE_TAG}
                    docker push ${BACKEND_ECR}:latest
                    docker push ${FRONTEND_ECR}:${IMAGE_TAG}
                    docker push ${FRONTEND_ECR}:latest
                    """

                    echo "🧹 Cleaning up local Docker images..."
                    sh "docker rmi ${BACKEND_ECR}:${IMAGE_TAG} ${BACKEND_ECR}:latest || true"
                    sh "docker rmi ${FRONTEND_ECR}:${IMAGE_TAG} ${FRONTEND_ECR}:latest || true"
                }
            }
        }

        /* ------------------- 9. Deploy to ECS ------------------- */
        stage('Deploy to ECS') {
            steps {
                echo "🚀 Triggering ECS deployment..."
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

        /* ------------------- 10. Upload Artifacts to S3 ------------------- */
        stage('Upload Artifacts to S3') {
            steps {
                script {
                    echo "📦 Uploading Trivy reports to S3 bucket: ${S3_BUCKET}"
                    sh """
                    aws s3 cp trivy-backend-report.txt s3://${S3_BUCKET}/reports/${BUILD_NUMBER}/backend.txt --region ${AWS_REGION}
                    aws s3 cp trivy-frontend-report.txt s3://${S3_BUCKET}/reports/${BUILD_NUMBER}/frontend.txt --region ${AWS_REGION}
                    """
                }
            }
        }
    }

    /* ------------------- Post Build Notifications ------------------- */
    post {
        success {
            echo "✅ Deployment successful! ECS is now running the latest images."
        }
        failure {
            echo "❌ Deployment failed. Please check build logs and Trivy reports."
        }
    }
}
