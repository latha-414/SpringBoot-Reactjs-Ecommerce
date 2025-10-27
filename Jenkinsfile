pipeline {
    agent any // Use a specific agent

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        BACKEND_IMAGE_TAG = "${env.BUILD_NUMBER}"
        FRONTEND_IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "📦 Checking out code from GitHub"
                retry(3) {
                    git branch: 'main', credentialsId: 'github-credentials', url: 'https://github.com/latha-414/SpringBoot-Reactjs-Ecommerce'
                }
            }
        }

        stage('Build Backend') {
            steps {
                dir('Ecommerce-Backend') {
                    echo "⚙️ Building backend with Maven"
                    timeout(time: 10, unit: 'MINUTES') {
                        retry(3) {
                            sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                        }
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
                withCredentials([string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID')]) {
                    script {
                        def BACKEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                        def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
                        timeout(time: 10, unit: 'MINUTES') {
                            echo "🐳 Building backend Docker image"
                            sh "docker build -t ${BACKEND_ECR}:${BACKEND_IMAGE_TAG} Ecommerce-Backend/"
                            echo "🐳 Building frontend Docker image"
                            sh "docker build -t ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG} Ecommerce-Frontend/"
                        }
                    }
                }
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-credentials']]) {
                    echo "🔐 Logging into AWS ECR"
                    timeout(time: 5, unit: 'MINUTES') {
                        retry(3) {
                            sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                        }
                    }
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                withCredentials([string(credentialsId: 'aws-account-id', variable: 'AWS_ACCOUNT_ID')]) {
                    script {
                        def BACKEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                        def FRONTEND_ECR = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
                        timeout(time: 10, unit: 'MINUTES') {
                            echo "🚀 Pushing backend Docker image to ECR"
                            sh "docker push ${BACKEND_ECR}:${BACKEND_IMAGE_TAG}"
                            sh "docker rmi ${BACKEND_ECR}:${BACKEND_IMAGE_TAG}"
                            echo "🚀 Pushing frontend Docker image to ECR"
                            sh "docker push ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG}"
                            sh "docker rmi ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG}"
                        }
                    }
                }
            }
        }

        stage('Deploy to ECS') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-ecr-credentials']]) {
                    timeout(time: 5, unit: 'MINUTES') {
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
                        echo "⏳ Waiting for services to stabilize"
                        sh """
                        aws ecs wait services-stable \
                            --cluster ecommerce-project-cluster \
                            --services ecommerce-project-backend-service ecommerce-project-frontend-service \
                            --region ${AWS_REGION}
                        """
                    }
                }
            }
        }
    }
}
