# Fancia

Fancia is a social platform connecting people with shared interests for offline, in-person group gatherings and community building.

## user

This project is one the microservices in [Fancia](https://github.com/fungkinchun/fancia)

[Swagger UI](http://fancia.co.uk/user/swagger-ui/index.html)

## Developer notes

### Initial database setup

Flyway is used for schema migrations. For quick local testing you can allow Hibernate to create the schema, but in this project we try to keep the local setup close to production.

Example (development only):

```yaml
hibernate:
  ddl-auto: create
```

To prepare the initial schema you can run the application with the local profile which will generate a baseline migration script in the resources directory:

```bash
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

Flyway migration scripts are stored in `src/main/resources/db/migration` (e.g. `V1__create_schema.sql`). After running the app in local mode, inspect the generated script and commit it to the repository so Flyway can apply it in CI and production.

Refer to `application-local.yaml` for example configuration. A minimal JPA snippet used in this project looks like:

```yaml
jpa:
  show-sql: true
  properties:
    hibernate:
      dialect: org.hibernate.dialect.PostgreSQLDialect
    jakarta:
      persistence:
        schema-generation:
          scripts:
            action: create
            create-target: src/main/resources/db/migration/V1__create_schema.sql
            create-source: metadata
hibernate:
  ddl-auto: create
```

For production, use Flyway to manage and apply migrations (use `ddl-auto: none`). Use the `prod` profile (for example `application-prod.yaml`) and ensure the migration scripts are applied as part of your deployment pipeline.
