# JDBC 라이브러리 구현하기
## DriverManager vs DataSource
DB 와의 커넥션을 획득하는 방법은 DriverManager 을 사용하는 방법, DataSource 를 사용하는 방법이 있다.  
  - 중간 계층의 인프라와 함께 동작하도록 구현된 DataSource 객체를 사용하면 Connection 풀링, Statement 풀링, 분산 트랜잭션이 가능해진다.
      - 분산 트랜잭션을 통해 애플리케이션은 단일 트랜잭션으로 복수의 서버에 존재하는 데이터 소스에 접근할 수 있다.
  - DriverManager 를 통해 만들어진 Connection 객체의 경우에는 위의 기능이 제공되지 않는다.
    
애플리케이션이 필요로 할 때마다 매번 DB 에 물리적 연결을 만들면 시간과 리소스가 많이 사용된다.(DriverManager)   
그래서 비용절감을 위해 Connection Pool 을 사용한다.(DataSource)

### 커넥션 풀링(HikariCP)
커넥션 풀링은 DB 사용시 커넥션을 재사용할 수 있도록 미리 캐싱하는 것이다.
HikariCP 는 JDBC 연결 풀링 프레임워크으로 빠르고 간단하고 신뢰성있고 가볍고 오버헤드가 없다고 한다. [README](https://github.com/brettwooldridge/HikariCP) 만 보면 완벽한 것 같다.
Spring Boot 2.0 부터 HikariCP 를 기본 dataSource 로 채택하고 있다.
> 우리는 성능과 동시성 때문에 HikariCP를 선호합니다.
> [스프링 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#data.sql.datasource.connection-pool)

#### HikariCP 권장 설정(MySQL 사용시)
```
jdbcUrl=jdbc:mysql://localhost:3306/simpsons
username=test
password=test
dataSource.cachePrepStmts=true
dataSource.prepStmtCacheSize=250
dataSource.prepStmtCacheSqlLimit=2048
dataSource.useServerPrepStmts=true
dataSource.useLocalSessionState=true
dataSource.rewriteBatchedStatements=true
dataSource.cacheResultSetMetadata=true
dataSource.cacheServerConfiguration=true
dataSource.elideSetAutoCommits=true
dataSource.maintainTimeStats=false
```
- cachePrepStmts
  -   캐시 사용 설정
- prepStmtCacheSize
  - MySQL 드라이버가 연결당 캐시할 준비된 명령문의 수를 설정한다.
  - 기본값은 25, 250~500 사이로 설정하는 것이 좋다.
- prepStmtCacheSqlLimit
  - 드라이버가 캐시할 준비된 SQL문의 최대 길이다.
  - MySQL 기본값은 256이다. ORM 프레임워크에서 권장 설정은 2048이다.
- userServerPrepStmts
  - 최신 버전의 MySQL 은 서버 측 준비 명령문을 지원하므로 상당한 성능 향상을 제공할 수 있다.

### Transaction 적용하기
#### step1
서비스 레이어에서 직접 connection 핸들링하여 Transaction 처리를 해준다.
1. connection.setAutoCommit(false);
2. connection.commit();
3. connection.rollback();
4. connection.setAutoCommit(true);
5. connection.close();
단점: Repository 레이어에서 connection을 파라미터로 받을 수 있도록 새로운 메서드를 만들어줘야 한다.   
해결법: 트랜잭션 동기화 방식 사용(PlatformTransactionManger)
#### step2
트랜잭션 동기화(Transaction synchronization) 방식을 사용한다.
1. transactionManager.getTransaction(definition);
    
    이때 definition은 전파 동작, 격리 수준, 시간 초과 등의 정보를 가진 인스턴스임.
    
    전파 옵션은 트랜잭션을 시작하거나 기존 트랜잭션에 참여하는 방법에 대해 결정하는 속성값이다.

트랜잭션 동기화란 트랜잭션을 시작하기 위한 Connection 객체를 따로 보관해두고, DAO에서 호출할 때 저장된 Connection을 가져다 사용하는 방식이다.
1. transactionManager.commit(transactionStatus);
2. transactionManager.rollback(transactionStatus);
DataSourceTransactionManager.getTransaction(definition)을 호출하면 내부에서connection.setAutoCommit(false)를 호출한다.
<img width="780" alt="스크린샷 2022-10-11 오전 12 26 56" src="https://github.com/Youngyoon-1/jwp-dashboard-jdbc/assets/76875654/1f749955-a1d4-4583-a4d3-abb3cc3bd6ab">
DataSourceTransactionManager 를 사용하면 commit, rollback 시 내부에서 setAutoCommit(true), DataSourceUtils.releaseConnection(connection, dataSource) 을 호출한다.

- 외부에서 관리되지 않는 경우(즉, 스레드에 바인딩되지 않은 경우) 지정된 DataSource에서 가져온 지정된 연결을 닫는다.

DataSourceUtils.getConnection(dataSource)를 호출하면 DataSourceTransactionManager을 사용할 경우 현재 스레드에 바인딩된 연결을 반환한다.

- 트랜잭션 동기화가 활성화되지 않았다면 새로운 연결을 반환한다.

단점: 트랜잭션 로직들과 서비스 레이어의 비즈니스 로직이 섞여있다.
<img width="911" alt="스크린샷 2022-10-11 오전 12 50 21" src="https://github.com/Youngyoon-1/jwp-dashboard-jdbc/assets/76875654/ba37a2f0-d7e5-4659-a482-eb8ac890b203">   
해결법: 트랜잭션 처리 역할을 분리한다.  

#### step3

트랜잭션 서비스 추상화하기

서비스 추상화를 통해서 비즈니스 로직과 트랜잭션 처리로직을 완전히 분리한다.

#### 왜 스프링은 PlatformTransactionManger로 트랜잭션을 관리할까?

인터페이스 이기 때문에 필요에 따라 mock, stub 해서 사용할 수 있다. 또한 개발자는 getTransaction(), commit(), rollback() 메서드만 알면 된다. connection 관리, 리소스 해제, autoCommit 설정 등과 같은 반복 작업은 구현체가 알아서 해준다.

## 1단계 요구사항
- [x] UserDaoTest의 모든 테스트 케이스 통과
- [x] JdbcTemplate 클래스에서 JDBC와 관련된 처리를 하도록 구현한다.

## 2단계 요구사항

- [x] 함수형 인터페이스 사용
- [x] 제네릭 사용
- [x] 가변 인자 사용
- [x] 람다식 사용
- [x] try-with-resources 사용
- [x] checked -> unchecked exception 변경

## 3단계 요구사항

- [x] User 비밀번호 변경 기능에 트랜잭션 적용
- [x] 트랜잭션 서비스와 애플리케이션 서비스 분리
