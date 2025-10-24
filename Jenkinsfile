pipeline {
    agent any

    environment {
        AWS_ACCOUNT_ID      = '474668399006' // Replace with your account ID or use Jenkins credentials
        AWS_REGION          = 'ap-south-1'
        BACKEND_ECR         = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
        FRONTEND_ECR        = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
        BACKEND_IMAGE_TAG   = "latest"
        FRONTEND_IMAGE_TAG  = "latest"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out code from GitHub"
                git branch: 'main', url: 'https://github.com/latha-414/SpringBoot-Reactjs-Ecommerce'
            }
        }

        stage('Build Backend') {
            steps {
                dir('Ecommerce-Backend') {
                    echo "Building backend with Maven"
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    // Backend Docker image
                    echo "Building backend Docker image"
                    sh "docker build -t ${BACKEND_ECR}:${BACKEND_IMAGE_TAG} Ecommerce-Backend/"
                    
                    // Frontend Docker image
                    echo "Building frontend Docker image"
                    sh "docker build -t ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG} Ecommerce-Frontend/"
                }
            }
        }

        stage('Login to ECR') {
            steps {
                echo "Logging into AWS ECR"
                sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
            }
        }

        stage('Push Docker Images') {
            steps {
                echo "Pushing backend Docker image to ECR"
                sh "docker push ${BACKEND_ECR}:${BACKEND_IMAGE_TAG}"

                echo "Pushing frontend Docker image to ECR"
                sh "docker push ${FRONTEND_ECR}:${FRONTEND_IMAGE_TAG}"
            }
        }

        stage('Deploy to ECS') {
            steps {
                script {
                    echo "Updating ECS backend service"
                    sh """
                    aws ecs update-service \
                        --cluster ecommerce-project-cluster \
                        --service ecommerce-project-backend-service \
                        --force-new-deployment
                    """

                    echo "Updating ECS frontend service"
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
            echo "Deployment completed successfully!"
        }
        failure {
            echo "Pipeline failed. Check the logs."
        }
    }
}
