pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
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

        stage('Build Docker Images') {
            steps {
                script {
                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh "docker build -t ${BACKEND_ECR}:${IMAGE_TAG} Ecommerce-Backend/"
                    sh "docker build -t ${FRONTEND_ECR}:${IMAGE_TAG} Ecommerce-Frontend/"
                }
            }
        }

        stage('Login to ECR') {
            steps {
                script {
                    def ECR_REGISTRY = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                }
            }
        }

        stage('Push Docker Images') {
            steps {
                script {
                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    sh "docker push ${BACKEND_ECR}:${IMAGE_TAG}"
                    sh "docker rmi ${BACKEND_ECR}:${IMAGE_TAG}"

                    sh "docker push ${FRONTEND_ECR}:${IMAGE_TAG}"
                    sh "docker rmi ${FRONTEND_ECR}:${IMAGE_TAG}"
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

                sh """
                aws ecs wait services-stable \
                    --cluster ecommerce-project-cluster \
                    --services ecommerce-project-backend-service ecommerce-project-frontend-service \
                    --region ${AWS_REGION}
                """
            }
        }
    }

    post {
        success {
            echo "Deployment successful!"
            echo "App URL: http://ecommerce-project-alb-1227317348.ap-south-1.elb.amazonaws.com"
        }
        failure {
            echo "Deployment failed. Check logs."
        }
    }
}
