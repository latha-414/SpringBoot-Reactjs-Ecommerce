pipeline {
    agent any

    // === PARAMETERS (Optional but Recommended) ===
    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Deployment environment')
    }

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        IMAGE_TAG = "${env.BUILD_NUMBER}-${sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()}"
        // Optional: Cache dirs
        NPM_CACHE = "${env.WORKSPACE}/.npm-cache"
    }

    stages {
        /* ---------------------------------------------------- */
        stage('Checkout') {
            steps {
                checkout scm
                echo "Checked out commit: ${env.GIT_COMMIT}"
            }
        }

        /* ---------------------------------------------------- */
        stage('Prerequisites') {
            steps {
                script {
                    // Validate tools early
                    sh 'command -v mvn >/dev/null && echo "Maven OK" || (echo "Maven missing" && exit 1)'
                    sh 'command -v npm >/dev/null && echo "npm OK" || (echo "npm missing" && exit 1)'
                    sh 'command -v docker >/dev/null && echo "Docker OK" || (echo "Docker missing" && exit 1)'
                    sh 'command -v trivy >/dev/null && echo "Trivy OK" || (echo "Trivy missing" && exit 1)'
                    sh 'command -v aws >/dev/null && echo "AWS CLI OK" || (echo "AWS CLI missing" && exit 1)'
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Build Backend') {
            steps {
                dir('Ecommerce-Backend') {
                    echo "Building backend..."
                    sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Build Frontend') {
            steps {
                dir('Ecommerce-Frontend') {
                    echo "Building frontend..."
                    sh "mkdir -p ${NPM_CACHE} && npm ci --cache ${NPM_CACHE} --prefer-offline"
                    sh 'npm run build'
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Get AWS Account ID') {
            steps {
                script {
                    env.AWS_ACCOUNT_ID = sh(
                        script: "aws sts get-caller-identity --query Account --output text",
                        returnStdout: true
                    ).trim()
                    echo "AWS Account: ${env.AWS_ACCOUNT_ID}"
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Build & Scan Docker Images') {
            steps {
                script {
                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    // Build
                    sh "docker build -t ${BACKEND_ECR}:${IMAGE_TAG} Ecommerce-Backend/"
                    sh "docker build -t ${FRONTEND_ECR}:${IMAGE_TAG} Ecommerce-Frontend/"

                    // TRIVY SCAN (Fail on HIGH/CRITICAL)
                    echo "Scanning images with Trivy..."
                    sh "trivy image --exit-code 1 --no-progress --severity HIGH,CRITICAL ${BACKEND_ECR}:${IMAGE_TAG}"
                    sh "trivy image --exit-code 1 --no-progress --severity HIGH,CRITICAL ${FRONTEND_ECR}:${IMAGE_TAG}"
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Login to ECR') {
            steps {
                script {
                    def ECR_REGISTRY = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                    sh """
                        retry() { n=0; until [ \$n -ge 3 ]; do \$@ && break; n=\$[\$n+1]; sleep 5; done; }
                        retry aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                    """
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Push Docker Images') {
            steps {
                script {
                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"

                    parallel(
                        backend: {
                            sh "docker push ${BACKEND_ECR}:${IMAGE_TAG}"
                            sh "docker rmi ${BACKEND_ECR}:${IMAGE_TAG} || true"
                        },
                        frontend: {
                            sh "docker push ${FRONTEND_ECR}:${IMAGE_TAG}"
                            sh "docker rmi ${FRONTEND_ECR}:${IMAGE_TAG} || true"
                        }
                    )
                }
            }
        }

        /* ---------------------------------------------------- */
        stage('Deploy to ECS') {
            steps {
                script {
                    def cluster = "ecommerce-project-cluster"
                    def backendSvc = "ecommerce-project-backend-service"
                    def frontendSvc = "ecommerce-project-frontend-service"

                    sh """
                        aws ecs update-service \
                            --cluster ${cluster} \
                            --service ${backendSvc} \
                            --task-definition ${backendSvc} \
                            --force-new-deployment \
                            --region ${AWS_REGION}
                    """
                    sh """
                        aws ecs update-service \
                            --cluster ${cluster} \
                            --service ${frontendSvc} \
                            --task-definition ${frontendSvc} \
                            --force-new-deployment \
                            --region ${AWS_REGION}
                    """
                    // Optional: Wait for stability
                    sh """
                        aws ecs wait services-stable \
                            --cluster ${cluster} \
                            --services ${backendSvc} ${frontendSvc} \
                            --region ${AWS_REGION} || echo "Warning: Services not stable yet"
                    """
                }
            }
        }
    }

    post {
        always {
            sh 'docker system prune -f || true'
            cleanWs(cleanWhenNotBuilt: false, deleteDirs: true)
        }
        success {
            echo "Deployment successful! Tag: ${IMAGE_TAG}"
            slackSend channel: '#deployments', color: 'good', message: "Deployed `${params.ENV}` | `${IMAGE_TAG}` | <${env.BUILD_URL}|View>"
        }
        failure {
            echo "Deployment failed."
            slackSend channel: '#deployments', color: 'danger', message: "Failed `${params.ENV}` | `${env.IMAGE_TAG ?: 'N/A'}` | <${env.BUILD_URL}|View>"
        }
    }
}
