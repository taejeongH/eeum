package org.ssafy.eeum.domain.iot.entity;

/**
 * IoT 기기 데이터 동기화 시 발생하는 작업의 종류를 정의하는 열거형 클래스입니다.
 * 앨범이나 메시지의 추가, 수정, 삭제 등의 액션을 구분합니다.
 * 
 * @summary 데이터 작업 타입 열거형
 */
public enum ActionType {
    /**
     * 새로운 데이터 추가
     */
    ADD,
    /**
     * 기존 데이터 수정
     */
    UPDATE,
    /**
     * 데이터 삭제
     */
    DELETE
}
