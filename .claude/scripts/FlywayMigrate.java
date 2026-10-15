import org.flywaydb.core.Flyway;

/** 데이터 변환 전후의 스키마 변경도 Flyway 이력과 체크섬을 유지한다. */
class FlywayMigrate {
    public static void main(String[] args) {
        var configuration = Flyway.configure()
            .dataSource(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
            .locations("filesystem:" + args[0]);
        if (!args[1].equals("latest")) configuration.target(args[1]);
        configuration.load().migrate();
    }
}
