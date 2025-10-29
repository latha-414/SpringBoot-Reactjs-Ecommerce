pipeline {
    agent any

    // === PARAMETERS ===
    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Select deployment environment')
    }

    // === OPTIONS ===
    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout(false)
    }

    // === GLOBAL ENVIRONMENT ===
    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.JAVA_HOME}/bin:/usr/local/bin:${env.PATH}"
        AWS_REGION = "${params.ENV == 'prod' ? 'ap-south-1' : 'us-east-1'}"
        NPM_CONFIG_CACHE = "${env.WORKSPACE}/.npm-cache"
        TRIVY_CACHE_DIR = "${env.WORKSPACE}/.trivy-cache"
        // Cache AWS Account ID across stages
        AWS_ACCOUNT_ID = ''
    }

    stages {
        // === 1. CHECKOUT CODE ===
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    echo "Branch: ${env.GIT_BRANCH} | Commit: ${env.GIT_COMMIT}"
                }
            }
        }

        // === 2. VALIDATE PREREQUISITES WITH VERSION LOGGING ===
        stage('Prerequisites') {
            steps {
                script {
                    def tools = [
                        'jq': 'jq --version',
                        'trivy': 'trivy --version',
                        'docker': 'docker --version',
                        'mvn': 'mvn --version',
                        'npm': 'npm --version'
                    ]
                    tools.each { name, cmd ->
                        echo "Checking ${name}..."
                        sh "${cmd} || (echo 'ERROR: ${name} failed' && exit 1)"
                    }
                }
            }
        }

        // === 3. SET IMAGE TAG (BUILD + GIT HASH) ===
        stage('Set Image Tag') {
            steps {
                script {
                    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    echo "Image Tag: ${env.IMAGE_TAG}"
                }
            }
        }

        // === 4. VALIDATE DOCKERFILES ===
        stage('Validate Dockerfiles') {
            steps {
                sh 'test -f Ecommerce-Backend/Dockerfile || (echo "Missing Dockerfile in Backend" && exit 1)'
                sh 'test -f Ecommerce-Frontend/Dockerfile || (echo "Missing Dockerfile in Frontend" && exit 1)'
            }
        }

        // === 5. BUILD PROJECTS (PARALLEL) ===
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
                            sh 'mkdir -p .npm-cache && npm ci --cache .npm-cache --prefer-offline'
                            sh 'npm run build'
                        }
                    }
                }
            }
        }

        // === 6. RUN TESTS (PARALLEL, CONDITIONAL FAILURE) ===
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
                                sh 'npm test -- --watch=false --browsers=ChromeHeadless'
                            }
                        }
                    }
                }
            }
            post {
                always {
                    script {
                        if (currentBuild.result == 'UNSTABLE') {
                            echo "Tests unstable — marking build UNSTABLE"
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
                failure {
                    script {
                        if (params.ENV == 'prod') {
                            error "CRITICAL: Test failure in PROD — blocking deployment"
                        } else {
                            echo "Test failure in ${params.ENV} — continuing (non-blocking)"
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
        }

        // === 7. GET AWS ACCOUNT ID (CACHED) ===
        stage('Get AWS Account ID') {
            steps {
                withAWS(credentials: 'aws-jenkins-credentials', region: "${AWS_REGION}") {
                    script {
                        if (!env.AWS_ACCOUNT_ID) {
                            env.AWS_ACCOUNT_ID = sh(
                                script: "aws sts get-caller-identity --query Account --output text",
                                returnStdout: true
                            ).trim()
                            echo "AWS Account ID: ${env.AWS_ACCOUNT_ID}"
                        } else {
                            echo "Using cached AWS Account ID: ${env.AWS_ACCOUNT_ID}"
                        }
                    }
                }
            }
        }

        // === 8. BUILD DOCKER IMAGES (LOCAL TAG) ===
        stage('Build Docker Images') {
            steps {
                script {
                    def BACKEND_REPO = "ecommerce-project-backend-${params.ENV}"
                    def FRONTEND_REPO = "ecommerce-project-frontend-${params.ENV}"
                    sh "docker build --pull --no-cache -t ${BACKEND_REPO}:${IMAGE_TAG} Ecommerce-Backend/"
                    sh "docker build --pull --no-cache -t ${FRONTEND_REPO}:${IMAGE_TAG} Ecommerce-Frontend/"
                }
            }
        }

        // === 9. SECURITY SCAN (LOCAL IMAGES) ===
        stage('Security Scan') {
            steps {
                script {
                    def BACKEND_LOCAL = "ecommerce-project-backend-${params.ENV}:${IMAGE_TAG}"
                    def FRONTEND_LOCAL = "ecommerce-project-frontend-${params.ENV}:${IMAGE_TAG}"
                    def severity = params.ENV == 'prod' ? 'HIGH,CRITICAL' : 'CRITICAL'
                    sh "mkdir -p ${TRIVY_CACHE_DIR}"
                    sh "trivy image --cache-dir ${TRIVY_CACHE_DIR} ${BACKEND_LOCAL} --exit-code 1 --no-progress --severity ${severity}"
                    sh "trivy image --cache-dir ${TRIVY_CACHE_DIR} ${FRONTEND_LOCAL} --exit-code 1 --no-progress --severity ${severity}"
                }
            }
        }

        // === 10. LOGIN TO ECR ===
        stage('Login to ECR') {
            steps {
                withAWS(credentials: 'aws-jenkins-credentials', region: "${AWS_REGION}") {
                    script {
                        def ECR_REGISTRY = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                        sh """
                            retry 3 aws ecr get-login-password --region ${AWS_REGION} | \
                            docker login --username AWS --password-stdin ${ECR_REGISTRY}
                        """
                    }
                }
            }
        }

        // === 11. TAG & PUSH IMAGES (FIXED :LATEST TAG) ===
        stage('Tag and Push Docker Images') {
            steps {
                script {
                    def BACKEND_REPO = "ecommerce-project-backend-${params.ENV}"
                    def FRONTEND_REPO = "ecommerce-project-frontend-${params.ENV}"
                    def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${BACKEND_REPO}"
                    def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${FRONTEND_REPO}"

                    def pushImage = { local, ecr, tag ->
                        sh "docker tag ${local}:${IMAGE_TAG} ${ecr}:${tag}"
                        sh "retry 3 docker push ${ecr}:${tag}"
                        sh "docker rmi ${ecr}:${tag}"
                    }

                    parallel(
                        backend: {
                            pushImage(BACKEND_REPO, BACKEND_ECR, IMAGE_TAG)
                            pushImage(BACKEND_REPO, BACKEND_ECR, 'latest')
                        },
                        frontend: {
                            pushImage(FRONTEND_REPO, FRONTEND_ECR, IMAGE_TAG)
                            pushImage(FRONTEND_REPO, FRONTEND_ECR, 'latest')
                        }
                    )
                }
            }
        }

        // === 12. UPDATE TASK DEFS & DEPLOY TO ECS (SAFER) ===
        stage('Update ECS Task Definitions and Deploy') {
            steps {
                withAWS(credentials: 'aws-jenkins-credentials', region: "${AWS_REGION}") {
                    script {
                        def cluster = "ecommerce-project-cluster"
                        def backendService = "ecommerce-project-backend-service"
                        def frontendService = "ecommerce-project-frontend-service"
                        def backendFamily = "ecommerce-backend-family"
                        def frontendFamily = "ecommerce-frontend-family"

                        def updateTaskDefImage = { family, image, containerIndex = 0 ->
                            def taskDef = sh(
                                script: "aws ecs describe-task-definition --task-definition ${family} --query 'taskDefinition' --output json --region ${AWS_REGION}",
                                returnStdout: true
                            )
                            def taskJson = readJSON text: taskDef
                            taskJson.containerDefinitions[containerIndex].image = image
                            def newJson = writeJSON returnText: true, json: taskJson
                            def registered = sh(
                                script: "aws ecs register-task-definition --cli-input-json '${newJson}' --region ${AWS_REGION}",
                                returnStdout: true
                            )
                            return sh(script: "echo '${registered}' | jq -r .taskDefinition.taskDefinitionArn", returnStdout: true).trim()
                        }

                        def BACKEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-backend-${params.ENV}:${IMAGE_TAG}"
                        def FRONTEND_ECR = "${env.AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/ecommerce-project-frontend-${params.ENV}:${IMAGE_TAG}"

                        def backendTaskArn = updateTaskDefImage(backendFamily, BACKEND_ECR)
                        def frontendTaskArn = updateTaskDefImage(frontendFamily, FRONTEND_ECR)

                        sh """
                            set -e
                            retry 3 aws ecs update-service --cluster ${cluster} --service ${backendService} --task-definition ${backendTaskArn} --force-new-deployment --region ${AWS_REGION}
                            retry 3 aws ecs update-service --cluster ${cluster} --service ${frontendService} --task-definition ${frontendTaskArn} --force-new-deployment --region ${AWS_REGION}
                            aws ecs wait services-stable --cluster ${cluster} --services ${backendService} ${frontendService} --region ${AWS_REGION} --waiter-config '{"Delay":10,"MaxAttempts":30}'
                        """
                    }
                }
            }
        }

        // === 13. SMOKE TEST (NON-PROD ONLY) ===
        stage('Smoke Test') {
            when { not { equals expected: 'prod', actual: params.ENV } }
            steps {
                withAWS(credentials: 'aws-jenkins-credentials', region: "${AWS_REGION}") {
                    script {
                        def albDns = sh(
                            script: "aws elbv2 describe-load-balancers --names ecommerce-alb --query 'LoadBalancers[0].DNSName' --output text --region ${AWS_REGION}",
                            returnStdout: true
                        ).trim()
                        def url = "http://${albDns}"
                        timeout(time: 5, unit: 'MINUTES') {
                            sh "curl -f --retry 10 --retry-delay 15 --max-time 300 ${url}/health || exit 1"
                        }
                    }
                }
            }
        }

        // === 14. ARCHIVE ARTIFACTS ===
        stage('Archive Artifacts') {
            steps {
                archiveArtifacts artifacts: 'Ecommerce-Backend/target/*.jar', allowEmptyArchive: true, fingerprint: true
                archiveArtifacts artifacts: 'Ecommerce-Frontend/dist/**', allowEmptyArchive: true, fingerprint: true
            }
        }
    }

    // === POST ACTIONS ===
    post {
        always {
            cleanWs(cleanWhenNotBuilt: false, deleteDirs: true)
            sh 'docker system prune -f || true'
        }
        success {
            echo "Deployment successful!"
            slackSend(
                channel: '#deployments',
                color: 'good',
                message: "*Deployment Succeeded* | `${params.ENV}` | `${IMAGE_TAG}` | <${env.BUILD_URL}|View Build>"
            )
        }
        failure {
            echo "Deployment failed. Check logs for details."
            slackSend(
                channel: '#deployments',
                color: 'danger',
                message: "*Deployment Failed* | `${params.ENV}` | `${IMAGE_TAG}` | <${env.BUILD_URL}|View Build>"
            )
        }
    }
}
