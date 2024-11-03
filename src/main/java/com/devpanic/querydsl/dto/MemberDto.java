package com.devpanic.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data // -> 기본 생성자는 안만들어줌
public class MemberDto {
    private String username;
    private int age;

    public MemberDto(){

    }

    @QueryProjection // -> Q 파일로 생성하기 위한 어노테이션
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
