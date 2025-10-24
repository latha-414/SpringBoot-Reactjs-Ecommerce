pipeline {
    agent any

    environment {
        AWS_REGION = 'ap-south-1'
        ECR_REPO = credentials('ECR_REPO_URL') // stored securely in Jenkins credentials
    }

    stages {
        stage('Frontend Build') {
            agent {
                docker {
                    image 'node:18-alpine'
                    args '-u root:root'
                }
            }
            steps {
                dir('Ecommerce-Frontend') {
                    echo '🎨 Building frontend using npm...'
                    sh 'npm install'
                    sh 'npm run build'
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                echo '🐳 Building Docker images...'
                sh 'docker build -t $ECR_REPO/frontend ./Ecommerce-Frontend'
                sh 'docker build -t $ECR_REPO/backend ./Ecommerce-Backend'
            }
        }

        stage('Login to ECR') {
            steps {
                echo '🔑 Logging in to ECR...'
                sh 'aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REPO'
            }
        }

        stage('Push Docker Images') {
            steps {
                echo '🚀 Pushing Docker images...'
                sh 'docker push $ECR_REPO/frontend'
                sh 'docker push $ECR_REPO/backend'
            }
        }

        stage('Deploy to ECS') {
            steps {
                echo '📦 Deploying to ECS...'
                sh 'aws ecs update-service --cluster ecommerce-cluster --service ecommerce-service --force-new-deployment --region $AWS_REGION'
            }
        }
    }

    post {
        success {
            echo '✅ Deployment completed successfully.'
        }
        failure {
            echo '❌ Pipeline failed. Please check logs for details.'
        }
    }
}
