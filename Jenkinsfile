pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_ACCOUNT_ID = '474668399006'
        AWS_REGION = 'ap-south-1'
        BACKEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
        FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
        BACKEND_IMAGE_TAG = "latest"
        FRONTEND_IMAGE_TAG = "latest"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "📦 Checking out code from GitHub"
                git branch: 'main', url: 'https://github.com/latha-414/SpringBoot-Reactjs-Ecommerce'
            }
        }

        stage('Build Backend') {
            steps {
                dir('Ecommerce-Backend') {
                    echo "⚙️ Building backend with Maven"
                    retry(3) {
                        sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                    }
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('Ecommerce-Frontend') {
                    echo "🎨 Building frontend with npm"
                    timeout(time: 10, unit: 'MINUTES') {
                        retry(3) {
                            sh 'npm install && npm run build'
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    echo "🐳 Building backend Docker image"
                    sh "docker build -t ${BACKEND_ECR}:${BACKEND_IMAGE_TAG} Ecommerce-Backend/"
                    
                    echo "🐳 Building frontend Docker image"
                    sh "docker build -t ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG} Ecommerce-Frontend/"
                }
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-credentials']]) {
                    echo "🔐 Logging into AWS ECR"
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                script {
                    echo "🚀 Pushing backend Docker image to ECR"
                    sh "docker push ${BACKEND_ECR}:${BACKEND_IMAGE_TAG}"

                    echo "🚀 Pushing frontend Docker image to ECR"
                    sh "docker push ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG}"
                }
            }
        }

        stage('Deploy to ECS') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-credentials']]) {
                    echo "🔄 Updating ECS backend service"
                    sh """
                    aws ecs update-service \
                        --cluster ecommerce-project-cluster \
                        --service ecommerce-project-backend-service \
                        --force-new-deployment
                    """

                    echo "🔄 Updating ECS frontend service"
                    sh """
                    aws ecs update-service \
                        --cluster ecommerce-project-cluster \
                        --service ecommerce-project-frontend-service \
                        --force-new-deployment
                    """
                }
            }
        }
    }

    post {
        success {
            echo "✅ Deployment completed successfully!"
        }
        failure {
            echo "❌ Pipeline failed. Check the logs."
        }
    }
}
