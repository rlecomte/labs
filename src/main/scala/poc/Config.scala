package poc

case class Config(
    httpBind: String = "0.0.0.0",
    httpPort: Int = 8080,
    postgresStoreHost: String = "localhost",
    postgresStoreDb: String = "postgres",
    postgresStoreUser: String = "postgres",
    postgresStorePort: Int = 5434,
    postgresStorePassword: String = "trololo"
)
