package com.devpanic.querydsl;

import static com.devpanic.querydsl.entity.QMember.member;
import static com.devpanic.querydsl.entity.QTeam.team;
import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;

import com.devpanic.querydsl.dto.MemberDto;
import com.devpanic.querydsl.dto.QMemberDto;
import com.devpanic.querydsl.dto.UserDto;
import com.devpanic.querydsl.entity.Member;
import com.devpanic.querydsl.entity.QMember;
import com.devpanic.querydsl.entity.Team;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL(){
        // member1 찾기
        String qlString = "select m from Member m where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl(){
        //가급적 static import 권장, 조인으로 인해 필요한 경우에만 따로 선언 권장
        //QMember m = new QMember("m");

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        Assertions.assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search(){
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.between(10, 30)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam(){
        // 검색 조건 and() 엮기는 , 로도 가능하다.
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
//        List<Member> fetch = queryFactory
//                .selectFrom(member)
//                .fetch();
//
//        Member fetchOne = queryFactory
//                .selectFrom(member)
//                .fetchOne();
//
//        Member fetchFirst = queryFactory
//                .selectFrom(member)
//                .fetchFirst();

        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

//        results.getTotal();
        List<Member> content = results.getResults();

//        long total = queryFactory.selectFrom(member)
//                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 나이 내림차순
     * 2. 이름 올림차순
     * 2에서 이름이 없는 경우 마지막에 출력 -> nulls last
     */
    @Test
    public void sort(){
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2(){
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation(){
        // Tuple은 query dsl 내 Tuple
        // 대부분은 tuple 보다는 dto로 추출하는 방식 선호
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연력을 구해라.
     */
    @Test
    public void group() throws Exception{
        //given
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15); // (10 + 20) / 2

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35); // (30 + 40) / 2
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인
     * 회원의 이름이 팀 이름과 같은 회원 조회
     */
    @Test
    public void theta_join(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인하며 팀 이름이 teamA인 팀 조인, 회원은 모두 조회
     * JPQL : select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    public void join_on_filtering(){
        // on이 join 조건이라고 생각했는데, 내부 조인일 경우의 where 절 기능도 함께 포함한다.
        // 때문에 정말 필요한 경우가 아니라면 내부 조인 조건은 where절로 사용하는게 좋다.
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                //.on(team.name.eq("teamA"))
                .where(team.name.eq("teamA"))
                .fetch();

        for(Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관 관계 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                // member.team -> 연관 관계 있는 조인
                // team -> 연관 관계 없는 조인
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for(Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery(){
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원 조회
     */
    @Test
    public void subQueryGoe(){
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    @Test
    public void subQueryIn(){
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    @Test
    public void selectSubQuery(){
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for(Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void basicCase(){
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase(){
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0 ~ 20살")
                        .when(member.age.between(21, 30)).then("21 ~ 30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();


        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant(){
        // 결과에서만 받게됨. 실제 쿼리 상에서는 상수를 찾아볼 수 없음
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for(Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat(){
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    @Test
    public void simpleProjection(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    // Tuple -> com.querydsl 소속
    // persistence 계층에 종속되는 개념으로 controller, service는 의존성이 없는게 좋다.
    @Test
    public void tupleProjection(){
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for(Tuple tuple : result){
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    // 생성자를 통한 방법밖에 쓸 수 없음
    @Test
    public void findDtoByJPQL(){
        List<MemberDto> result = em.createQuery(
                "select new com.devpanic.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // Projection은 기본 생성자가 있어야 한다.
    // .bean()은 getter, setter 필요
    @Test
    public void findDtoBySetter(){
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // getter, setter 필요 없음. 리플렉션 방식 사용
    @Test
    public void findDtoByField(){
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    // alias -> .as() 이용
    // field를 이용한 방식은 변수명이 매핑이 되어야만 한다.
    @Test
    public void findUserDto(){
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), // ExpressionUtils.as(member.username, "name")
                        // subquery
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for(UserDto userDto : result){
            System.out.println("userDto = " + userDto);
        }
    }

    // 존재하는 생성자와 매핑하기에 인자의 순서, 타입이 중요
    // 생성자를 이용한 방식은 인자의 이름은 상관 없다.
    @Test
    public void findDtoByConstructor(){
        List<UserDto> result = queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for(UserDto userDto : result){
            System.out.println("userDto = " + userDto);
        }
    }

    // 만약에 엔티티를 받아오는게 아닌 DTO를 받아오는 경우, Q class가 없기에 컴파일 오류를 잡을수 없다.
    // -> @QueryProjection 사용!
    // 다만 DTO에 querydsl 의존성이 생기는 단점이 있음
    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for(MemberDto memberDto : result){
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond){
        BooleanBuilder builder = new BooleanBuilder();

        if(usernameCond != null){
            builder.and(member.username.eq(usernameCond));
        }

        if(ageCond != null){
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    // 이 방식은 메소드 명에 조건이 표현되기 때문에 query와 함께 볼 수 있다는 장점이 있음
    @Test
    public void dynamicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond){
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        if(usernameCond == null){
            return null;
        }

        return member.username.eq(usernameCond);

//        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? ageEq(ageCond) : null;
    }

    private Predicate allEq(String usernameCond, Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    private BooleanExpression isServicable(String usernameCond, Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    // 영속성 컨텍스트를 무시하고 바로 insert
    // 아래의 경우 result의 내용이 "비회원"으로 업데이트 되었음을 기대
    // But ! 영속성 컨텍스트에 이미 member1 ~ 4가 저장되어 있고 db에서 가져온 내용을 버리게 됨
    // So ! 기대와는 다르게 "비회원"이 아닌 값이 들어가게 됨
    // Because - jdbc를 이용한 데이터 조작 방식은 영속성 컨텍스트로 관리되지 않기 때문 (Repeatable Read)
    // How to solve ? flush(), clear()를 이용하자 (그러나 이는 영속성 컨텍스트를 직접 컨트롤하기에 좋지 않은 것으로 알고 있음)
    @Test
    public void bulkUpdate(){
        // member1 = 10 -> 비회원
        // member2 = 20 -> 비회원
        // member3 = 30 -> 유지
        // member4 = 40 -> 유지
        
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for(Member member : result){
            System.out.println("member = " + member);
        }
    }

    @Test
    public void bulkAdd(){
        queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) // minus -> add(-1)
                .execute();
    }

    @Test
    public void bulkDelete(){
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

        System.out.println("count = " + count);
    }

    // DB 방언에 따라 지원하는 함수를 사용할 수 있다.
    @Test
    public void sqlFunction(){
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M")
                )
                .from(member)
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate(
//                        "function('lower', {0})",
//                        member.username)))
                .where(member.username.eq(member.username.lower())) // 간단한 ansi 표준 제공 함수는 이렇게 사용할 수 있다.
                .fetch();

        for(String s : result){
            System.out.println("s = " + s);
        }
    }
}
