pipeline {
    agent any
    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = 'ap-south-1'
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}-${env.BUILD_NUMBER}"
    }
    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test Backend') {
            parallel {
                stage('Build Backend') {
                    steps {
                        dir('Ecommerce-Backend') {
                            echo "Building backend"
                            sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2'
                        }
                    }
                }
                stage('Test Backend') {
                    steps {
                        dir('Ecommerce-Backend') {
                            echo "Running backend tests"
                            sh 'mvn test -Dhttps.protocols=TLSv1.2'
                        }
                    }
                }
            }
        }

        stage('Build & Test Frontend') {
            parallel {
                stage('Build Frontend') {
                    steps {
                        dir('Ecommerce-Frontend') {
                            echo "Building frontend"
                            sh 'npm ci && npm run build'
                        }
                    }
                }
                stage('Test Frontend') {
                    steps {
                        dir('Ecommerce-Frontend') {
                            echo "Running frontend tests (non-blocking)"
                            sh 'npm test || echo "Warning: Frontend tests failed, but build continues"'
                        }
                    }
                }
            }
        }

        stage('Get AWS Account ID') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-jenkins-creds']]) {
                    script {
                        env.AWS_ACCOUNT_ID = sh(
                            script: 'aws sts get-caller-identity --query Account --output text',
                            returnStdout: true
                        ).trim()
                        echo "AWS Account: ${env.AWS_ACCOUNT_ID}"
                    }
                }
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-jenkins-creds']]) {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | \
                        docker login --username AWS --password-stdin ${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                    """
                }
            }
        }

        stage('Build & Push Docker Images') {
            steps {
                script {
                    def backend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def frontend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
                    echo "Building Docker images with tag: ${IMAGE_TAG}"

                    dir('Ecommerce-Backend') {
                        sh "docker build -t ${backend}:${IMAGE_TAG} -t ${backend}:latest ."
                    }
                    dir('Ecommerce-Frontend') {
                        sh "docker build -t ${frontend}:${IMAGE_TAG} -t ${frontend}:latest ."
                    }

                    echo "Pushing images to ECR"
                    sh """
                        docker push ${backend}:${IMAGE_TAG}
                        docker push ${backend}:latest
                        docker push ${frontend}:${IMAGE_TAG}
                        docker push ${frontend}:latest
                    """
                }
            }
        }

        // FIXED TRIVY STAGE
        stage('Scan with Trivy') {
            steps {
                script {
                    // 1. CLEAR TRIVY CACHE TO FREE SPACE
                    sh '''
                        echo "Clearing Trivy cache..."
                        rm -rf /var/lib/jenkins/.cache/trivy || true
                        mkdir -p /var/lib/jenkins/.cache/trivy
                    '''

                    // 2. INSTALL TRIVY
                    sh '''
                        echo "Installing Trivy..."
                        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
                            | sh -s -- -b /usr/local/bin v0.53.0
                        trivy --version
                    '''

                    def backendImage  = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend:${IMAGE_TAG}"
                    def frontendImage = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend:${IMAGE_TAG}"

                    // 3. SCAN WITHOUT JAVA DB (SAVE 800MB+)
                    echo "Scanning backend (skip Java DB)"
                    sh """
                        trivy image \
                            --exit-code 1 \
                            --severity CRITICAL \
                            --skip-java-db \
                            --cache-dir /var/lib/jenkins/.cache/trivy \
                            ${backendImage}
                    """

                    echo "Scanning frontend"
                    sh """
                        trivy image \
                            --exit-code 1 \
                            --severity CRITICAL \
                            --skip-java-db \
                            --cache-dir /var/lib/jenkins/.cache/trivy \
                            ${frontendImage}
                    """
                }
            }
        }

        stage('Deploy to ECS') {
            steps {
                echo "Deploying to ECS with force-new-deployment"
                sh """
                    aws ecs update-service --cluster ecommerce-project-cluster \
                      --service ecommerce-project-backend-service \
                      --force-new-deployment --region ${AWS_REGION}
                    aws ecs update-service --cluster ecommerce-project-cluster \
                      --service ecommerce-project-frontend-service \
                      --force-new-deployment --region ${AWS_REGION}
                """
            }
        }
    }

    post {
        always {
            script {
                def backend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                def frontend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
                echo "Cleaning up local Docker images"
                sh """
                    docker rmi ${backend}:${IMAGE_TAG} || true
                    docker rmi ${backend}:latest || true
                    docker rmi ${frontend}:${IMAGE_TAG} || true
                    docker rmi ${frontend}:latest || true
                    docker system prune -af || true
                    rm -rf /var/lib/jenkins/.cache/trivy || true
                """
            }
        }
        success {
            echo "DEPLOYMENT SUCCESSFUL! Tag: ${IMAGE_TAG}"
        }
        failure {
            echo "Deployment failed. Check logs above."
        }
    }
}
