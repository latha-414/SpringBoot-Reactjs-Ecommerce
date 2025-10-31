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
        stage('Checkout') { steps { checkout scm } }

        stage('Build & Test Backend') {
            parallel {
                stage('Build') { steps { dir('Ecommerce-Backend') { sh 'mvn clean package -DskipTests -Dhttps.protocols=TLSv1.2' } } }
                stage('Test')  { steps { dir('Ecommerce-Backend') { sh 'mvn test -Dhttps.protocols=TLSv1.2' } } }
            }
        }

        stage('Build & Test Frontend') {
            parallel {
                stage('Build') { steps { dir('Ecommerce-Frontend') { sh 'npm ci && npm run build' } } }
                stage('Test')  { steps { dir('Ecommerce-Frontend') { sh 'npm test || true' } } }
            }
        }

        stage('Get AWS Account ID') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-jenkins-creds']]) {
                    script { 
                        env.AWS_ACCOUNT_ID = sh(script: 'aws sts get-caller-identity --query Account --output text', returnStdout: true).trim()
                    }
                }
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-jenkins-creds']]) {
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                }
            }
        }

        stage('Build & Push Docker Images') {
            steps {
                script {
                    def backend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                    def frontend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
                    dir('Ecommerce-Backend')  { sh "docker build -t ${backend}:${IMAGE_TAG} -t ${backend}:latest ." }
                    dir('Ecommerce-Frontend') { sh "docker build -t ${frontend}:${IMAGE_TAG} -t ${frontend}:latest ." }
                    sh "docker push ${backend}:${IMAGE_TAG} && docker push ${backend}:latest && docker push ${frontend}:${IMAGE_TAG} && docker push ${frontend}:latest"
                }
            }
        }

        // FINAL TRIVY STAGE — ALLOW FIRST RUN, SKIP JAVA DB AFTER
        stage('Scan with Trivy') {
            steps {
                script {
                    sh '''
                        echo "Installing Trivy..."
                        mkdir -p ~/bin
                        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b ~/bin latest
                        export PATH="$HOME/bin:$PATH"
                        trivy --version
                    '''

                    def backendImage  = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend:${IMAGE_TAG}"
                    def frontendImage = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend:${IMAGE_TAG}"

                    // Check if Java DB exists → skip update if yes
                    def skipJavaDb = sh(script: "test -f ~/.cache/trivy/java-db/trivy-java.db && echo true || echo false", returnStdout: true).trim()

                    echo "Scanning backend"
                    sh """
                        trivy image \
                            --exit-code 1 \
                            --severity CRITICAL \
                            ${skipJavaDb == 'true' ? '--skip-java-db-update' : ''} \
                            --cache-dir ~/.cache/trivy \
                            ${backendImage}
                    """

                    echo "Scanning frontend"
                    sh """
                        trivy image \
                            --exit-code 1 \
                            --severity CRITICAL \
                            ${skipJavaDb == 'true' ? '--skip-java-db-update' : ''} \
                            --cache-dir ~/.cache/trivy \
                            ${frontendImage}
                    """
                }
            }
        }

        stage('Deploy to ECS') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-jenkins-creds']]) {
                    sh """
                        aws ecs update-service --cluster ecommerce-project-cluster --service ecommerce-project-backend-service --force-new-deployment --region ${AWS_REGION}
                        aws ecs update-service --cluster ecommerce-project-cluster --service ecommerce-project-frontend-service --force-new-deployment --region ${AWS_REGION}
                    """
                }
            }
        }
    }

    post {
        always {
            script {
                def backend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend"
                def frontend = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend"
                sh """
                    docker rmi ${backend}:${IMAGE_TAG} || true
                    docker rmi ${backend}:latest || true
                    docker rmi ${frontend}:${IMAGE_TAG} || true
                    docker rmi ${frontend}:latest || true
                    docker system prune -af || true
                    rm -rf ~/.cache/trivy || true
                    rm -rf ~/bin/trivy || true
                """
            }
        }
        success { echo "DEPLOYMENT SUCCESSFUL! Tag: ${IMAGE_TAG}" }
        failure { echo "Deployment failed." }
    }
}
