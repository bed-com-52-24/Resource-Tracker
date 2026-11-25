workspace "Resource Tracker" "Resource request, approval, stock and procurement management" {

    configuration {
        scope softwaresystem
    }

    model {
        // People
        admin  = person "Admin" "Manages users, teams, reassignments"
        md     = person "Managing Director" "Views all data, budgets, reports"
        dm     = person "Department Manager" "Decides on department requests"
        tl     = person "Team Lead" "Submits requests for their teams"
        member = person "Member" "Views team resources"

        // External systems
        smtp   = softwareSystem "SMTP Provider" "Delivers setup, reset and notification emails" "External"
        github = softwareSystem "GitHub and Actions" "Source control and CI/CD" "External"

        // Our system
        rt = softwareSystem "Resource Tracker" "Requests, approvals, stock, procurement and budgets" {

            !docs documentation
            !adrs decisions

            web = container "Web Client" "User interface for all roles" "Web frontend" "WebApp"

            nginx = container "Nginx Reverse Proxy" "HTTPS termination and reverse proxy" "Nginx + certbot" "Proxy"

            api = container "Resource Tracker API" "Modular monolith exposing REST /api/v1" "Spring Boot, Kotlin, Java 21" "Backend" {

                corrFilter  = component "CorrelationIdFilter" "Tags each request with a correlation ID" "Servlet filter"
                jwtFilter   = component "JWT Authentication" "Validates access (30 min) and refresh (7 d) tokens" "Spring Security, JJWT"
                rbac        = component "RBAC Interceptor" "Enforces role and team/department scope" "Spring interceptor"
                controllers = component "REST Controllers" "Validation, structured errors, OpenAPI docs" "Spring MVC"

                authSvc     = component "Auth and User Service" "Login, refresh, password tokens, users" "Spring service"
                orgSvc      = component "Organisation Services" "Departments, teams, projects, vendors" "Spring services"
                requestSvc  = component "Request Workflow Service" "Status lifecycle and manager routing" "Spring service"
                stockSvc    = component "Stock and Reservation Services" "Availability, reservations, waitlist, issue/return" "Spring services"
                procSvc     = component "Procurement and Budget Services" "External procurement, budgets, overrides" "Spring services"
                reportSvc   = component "Reports and Dashboards" "Inventory reports and analytics" "Spring service"
                notifSvc    = component "Notification and Mail Service" "In-app notifications and email" "Spring service, JTE"
                auditAspect = component "Audit Aspect" "Append-only audit in the same transaction" "Spring AOP"
                repos       = component "Repositories" "Used by every domain service (JPA)" "Spring Data JPA"
            }

            db = container "MySQL Database" "System of record and audit history" "MySQL 8 + Flyway" "Database"
        }

        // Context relationships
        admin  -> rt "Administers users and structure" "HTTPS"
        md     -> rt "Reviews budgets, reports and history" "HTTPS"
        dm     -> rt "Reviews requests" "HTTPS"
        tl     -> rt "Submits requests" "HTTPS"
        member -> rt "Views team resources" "HTTPS"
        rt     -> smtp "Sends emails" "SMTP"
        github -> rt "Builds, migrates and deploys" "GitHub Actions, SSH"

        // Container relationships
        admin  -> web "Uses" "HTTPS"
        md     -> web "Uses" "HTTPS"
        dm     -> web "Uses" "HTTPS"
        tl     -> web "Uses" "HTTPS"
        member -> web "Uses" "HTTPS"
        web    -> nginx "Calls API" "HTTPS/JSON"
        nginx  -> api "Forwards requests" "HTTP"
        api    -> db "Reads and writes" "JDBC"
        api    -> smtp "Sends emails" "SMTP"

        // Component relationships
        nginx       -> corrFilter "Forwards requests" "HTTP"
        corrFilter  -> jwtFilter  "Passes request" "Servlet filter chain"
        jwtFilter   -> rbac       "Passes principal" "Spring Security"
        rbac        -> controllers "Passes authorised request" "Spring MVC"
        controllers -> authSvc    "Delegates login and user calls" "Method call"
        controllers -> orgSvc     "Delegates organisation calls" "Method call"
        controllers -> requestSvc "Delegates request workflow calls" "Method call"
        controllers -> stockSvc   "Delegates stock calls" "Method call"
        controllers -> procSvc    "Delegates procurement and budget calls" "Method call"
        controllers -> reportSvc  "Delegates report calls" "Method call"
        requestSvc  -> stockSvc   "Reserves stock" "Method call"
        requestSvc  -> procSvc    "Checks budget" "Method call"
        requestSvc  -> notifSvc   "Notifies managers and leads" "Method call"
        authSvc     -> notifSvc   "Sends setup and reset codes" "Method call"
        requestSvc  -> auditAspect "Audited actions" "Spring AOP"
        authSvc     -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        orgSvc      -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        requestSvc  -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        stockSvc    -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        procSvc     -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        reportSvc   -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        notifSvc    -> repos      "Reads and writes" "Spring Data JPA" "Persistence"
        auditAspect -> repos      "Saves audit entries" "Spring Data JPA" "Persistence"
        repos       -> db         "Runs SQL" "JDBC"
        notifSvc    -> smtp       "Sends email" "SMTP"

        // Deployment
        production = deploymentEnvironment "Production" {
            deploymentNode "User device" "Any device used to reach the web client" "Browser" {
                containerInstance web
            }
            deploymentNode "AWS Lightsail" "Ubuntu 24.04 VPS" "AWS Lightsail" {
                deploymentNode "Nginx" "Reverse proxy with certbot HTTPS" "Nginx" {
                    containerInstance nginx
                }
                deploymentNode "systemd service" "/opt/resource-tracker/app.jar with JAR backups" "systemd" {
                    deploymentNode "JVM" "Java runtime that executes the application JAR" "Java 21" {
                        containerInstance api
                    }
                }
                deploymentNode "MySQL Server" "Native install" "MySQL 8" {
                    containerInstance db
                }
            }
        }
    }

    views {
        systemContext rt "1-SystemContext" "Who uses Resource Tracker and what it depends on." {
            include *
            autolayout tb 300 250
        }

        container rt "2-Containers" "Runtime building blocks." {
            include *
            autolayout tb 300 250
        }

        component api "3a-SecurityPipeline" "Every request passes this chain before reaching a controller." {
            include nginx corrFilter jwtFilter rbac controllers
            autolayout lr 250 200
        }

        component api "3b-DomainServices" "Controllers, domain services, audit and persistence (service-to-repository links omitted for clarity)." {
            include controllers authSvc orgSvc requestSvc stockSvc procSvc reportSvc notifSvc auditAspect repos db smtp
            exclude "relationship.tag==Persistence"
            autolayout tb 250 200
        }

        deployment rt production "4-Deployment" "How it runs on Lightsail." {
            include *
            autolayout lr 300 200
        }

        dynamic api "5-Login" "Login: the response returns along the same path." {
            web         -> nginx       "1. POST /auth/login"
            nginx       -> corrFilter  "2. Forward"
            corrFilter  -> jwtFilter   "3. Public route"
            jwtFilter   -> rbac        "4. Principal"
            rbac        -> controllers "5. Allowed"
            controllers -> authSvc     "6. login()"
            authSvc     -> repos       "7. Load user, save token"
            repos       -> db          "8. SQL"
            autolayout lr 250 200
        }

        dynamic rt "6a-SubmitRequest" "Team Lead submits a request." {
            tl    -> web   "1. Submit request"
            web   -> nginx "2. POST /requests"
            nginx -> api   "3. Forward"
            api   -> db    "4. Save PENDING, route, audit"
            api   -> smtp  "5. Notify manager"
            autolayout lr 250 200
        }

        dynamic rt "6b-DecideRequest" "Department Manager decides." {
            dm    -> web   "1. Approve, reject or query"
            web   -> nginx "2. Decision call"
            nginx -> api   "3. Forward"
            api   -> db    "4. Status, budget, stock, audit"
            api   -> smtp  "5. Notify Team Lead"
            autolayout lr 250 200
        }

        styles {
            element "Person" {
                shape Person
                background #14213d
                color #ffffff
            }
            element "Software System" {
                background #2f6fed
                color #ffffff
            }
            element "External" {
                background #8a93a8
                color #ffffff
            }
            element "Container" {
                background #3b82f6
                color #ffffff
            }
            element "Component" {
                background #6aa6ff
                color #0b1b3a
            }
            element "WebApp" {
                shape WebBrowser
            }
            element "Proxy" {
                background #7b4fd6
            }
            element "Backend" {
                background #2e9e6b
            }
            element "Database" {
                shape Cylinder
                background #e8832a
            }
            relationship "Relationship" {
                thickness 2
                color #5b6478
                dashed false
                fontSize 22
                width 260
            }
        }

        theme default
    }
}
