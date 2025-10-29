pipeline {
    agent any

    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Select deployment environment')
    }

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = "${params.ENV == 'prod' ? 'ap-south-1' : 'us-east-1'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prerequisites') {
            steps {
                sh 'command -v jq >/dev/null || (echo "ERROR: jq is required" && exit 1)'
                sh 'command -v trivy >/dev/null || (echo "ERROR: trivy is required" && exit 1)'
                sh 'command -v docker >/dev/null || (echo "ERROR: docker is required" && exit 1)'
            }
        }

        stage('Set Image Tag') {
            steps {
                script {
                    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    echo "Image Tag: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Validate Dockerfiles') {
            steps {
                sh 'test -f Ecommerce-Backend/Dockerfile || (echo "Missing Dockerfile in Backend" && exit 1)'
                sh 'test -f Ecommerce-Frontend/Dockerfile || (echo "Missing Dockerfile in Frontend" && exit 1)'
            }
        }

        stage('Build Projects') {
            parallel {
                stage('Build Backend') {
                    steps {
                        dir('Ecommerce-Backend') {
                            sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                        }
                    }
                }
                stage('Build Frontend') {
                    steps {
                        dir('Ecommerce-Frontend') {
                            sh 'npm install && npm run build'
                        }
                    }
                }
            }
        }

        stage('Run Tests') {
            parallel {
                stage('Test Backend') {
                    steps {
                        dir('Ecommerce-Backend') {
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh 'mvn test -Dhttps.protocols=TLSv1.2'
                            }
                        }
                    }
                }
                stage('Test Frontend') {
                    steps {
                        dir('Ecommerce-Frontend') {
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                sh 'npm test'
                            }
                        }
                    }
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
                    echo "AWS Account ID: ${env.AWS_ACCOUNT_ID}"
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def BACKEND_REPO = "ecommerce-project-backend-${params.ENV}"
                    def FRONTEND_REPO = "ecommerce-project-frontend-${params.ENV}"
                    sh "docker build -t ${BACKEND_REPO}:${IMAGE_TAG} Ecommerce-Backend/"
                    sh "docker build -t ${FRONTEND_REPO}:${IMAGE_TAG} Ecommerce-Frontend/"
                }
            }
        }

        stage('Security Scan') {
            steps {
                script {
                    def BACKEND_LOCAL = "ecommerce-project-backend-${params.ENV}:${IMAGE_TAG}"
                    def FRONTEND_LOCAL = "ecommerce-project-frontend-${params.ENV}:${IMAGE_TAG}"
                    def severity = params.ENV == 'prod' ? 'HIGH,CRITICAL' : 'CRITICAL'
                    sh "trivy image ${BACKEND_LOCAL} --exit-code 1 --no-progress --severity ${severity}"
                    sh "trivy image ${FRONTEND_LOCAL} --exit-code 1 --no-progress --severity ${severity}"
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

        stage('Tag and Push Docker Images') {
            steps {
                script {
                    def BACKEND_REPO = "ecommerce-project-backend-${params.ENV}"
                    def FRONTEND_REPO = "ecommerce-project-frontend-${params.ENV}"
                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${BACKEND_REPO}"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${FRONTEND_REPO}"

                    sh "docker tag ${BACKEND_REPO}:${IMAGE_TAG} ${BACKEND_ECR}:${IMAGE_TAG}"
                    sh "docker tag ${FRONTEND_REPO}:${IMAGE_TAG} ${FRONTEND_ECR}:${IMAGE_TAG}"

                    sh "docker tag ${BACKEND_ECR}:${IMAGE_TAG} ${BACKEND_ECR}:latest"
                    sh "docker tag ${FRONTEND_ECR}:${IMAGE_TAG} ${FRONTEND_ECR}:latest"

                    sh "docker push ${BACKEND_ECR}:${IMAGE_TAG}"
                    sh "docker push ${FRONTEND_ECR}:${IMAGE_TAG}"
                    sh "docker push ${BACKEND_ECR}:latest"
                    sh "docker push ${FRONTEND_ECR}:latest"

                    sh "docker rmi ${BACKEND_ECR}:${IMAGE_TAG} ${BACKEND_ECR}:latest"
                    sh "docker rmi ${FRONTEND_ECR}:${IMAGE_TAG} ${FRONTEND_ECR}:latest"
                }
            }
        }

        stage('Update ECS Task Definitions and Deploy') {
            steps {
                script {
                    def cluster = "ecommerce-project-cluster"
                    def backendService = "ecommerce-project-backend-service"
                    def frontendService = "ecommerce-project-frontend-service"
                    def backendFamily = "ecommerce-backend-family"
                    def frontendFamily = "ecommerce-frontend-family"

                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend-${params.ENV}:${IMAGE_TAG}"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend-${params.ENV}:${IMAGE_TAG}"

                    def updateTaskDef = { family, image ->
                        def taskDef = sh(
                            script: """
                                aws ecs describe-task-definition \
                                    --task-definition ${family} \
                                    --query 'taskDefinition' \
                                    --output json \
                                    --region ${AWS_REGION}
                            """,
                            returnStdout: true
                        )
                        def taskJson = readJSON text: taskDef
                        taskJson.containerDefinitions[0].image = image
                        def newJson = writeJSON returnText: true, json: taskJson
                        def registered = sh(
                            script: """
                                aws ecs register-task-definition \
                                    --cli-input-json '${newJson}' \
                                    --region ${AWS_REGION}
                            """,
                            returnStdout: true
                        )
                        return sh(script: "echo '${registered}' | jq -r .taskDefinition.taskDefinitionArn", returnStdout: true).trim()
                    }

                    def backendTaskArn = updateTaskDef(backendFamily, BACKEND_ECR)
                    def frontendTaskArn = updateTaskDef(frontendFamily, FRONTEND_ECR)

                    sh """
                        set -e
                        aws ecs update-service --cluster ${cluster} --service ${backendService} --task-definition ${backendTaskArn} --force-new-deployment --region ${AWS_REGION}
                        aws ecs update-service --cluster ${cluster} --service ${frontendService} --task-definition ${frontendTaskArn} --force-new-deployment --region ${AWS_REGION}
                        aws ecs wait services-stable \
                            --cluster ${cluster} \
                            --services ${backendService} ${frontendService} \
                            --region ${AWS_REGION} \
                            --waiter-config '{"Delay":10,"MaxAttempts":30}'
                    """
                }
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts artifacts: 'Ecommerce-Backend/target/*.jar', allowEmptyArchive: true
                archiveArtifacts artifacts: 'Ecommerce-Frontend/dist/**', allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            cleanWs()
            sh 'docker system prune -f'
        }
        success {
            echo "✅ Deployment successful!"
        }
        failure {
            echo "❌ Deployment failed. Check logs for details."
        }
    }
}
