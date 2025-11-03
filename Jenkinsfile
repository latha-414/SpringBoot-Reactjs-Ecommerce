pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Backend (Maven)') {
            steps {
                dir('Ecommerce-Backend') {
                    sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                }
            }
        }

        stage('Build Frontend (npm)') {
            steps {
                dir('Ecommerce-Frontend') {
                    sh 'npm install && npm run build'
                }
            }
        }

        stage('Get AWS Account ID') {
            steps {
                script {
                    env.AWS_ACCOUNT_ID = sh(
                        script: "aws sts get-caller-identity --query Account --output text",
                        returnStdout: true
                    ).trim()
                    echo "Account ID: ${env.AWS_ACCOUNT_ID}"
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def BACKEND_ECR  = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh """
                    docker build -t ${BACKEND_ECR}:${IMAGE_TAG} -t ${BACKEND_ECR}:latest Ecommerce-Backend/
                    docker build -t ${FRONTEND_ECR}:${IMAGE_TAG} -t ${FRONTEND_ECR}:latest Ecommerce-Frontend/
                    """
                }
            }
        }

        stage('Scan Docker Images with Trivy') {
            steps {
                script {
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

        stage('Login to ECR') {
            steps {
                script {
                    def ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                script {
                    def BACKEND_ECR  = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh """
                    docker push ${BACKEND_ECR}:${IMAGE_TAG}
                    docker push ${BACKEND_ECR}:latest
                    docker push ${FRONTEND_ECR}:${IMAGE_TAG}
                    docker push ${FRONTEND_ECR}:latest
                    """

                    sh "docker rmi ${BACKEND_ECR}:${IMAGE_TAG} ${BACKEND_ECR}:latest || true"
                    sh "docker rmi ${FRONTEND_ECR}:${IMAGE_TAG} ${FRONTEND_ECR}:latest || true"
                }
            }
        }

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

        stage('Upload Artifacts to S3') {
            steps {
                script {
                    echo "📦 Uploading artifacts to S3 using IAM Role dynamic bucket selection..."
                    sh """
                    aws s3 cp trivy-backend-report.txt s3://\$(aws s3 ls | awk 'NR==1{print \$3}')/reports/trivy-backend-report-${IMAGE_TAG}.txt --region ${AWS_REGION} || true
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ Deployment successful! ECS is using latest images."
        }
        failure {
            echo "❌ Deployment failed. Check build logs."
        }
    }
}
