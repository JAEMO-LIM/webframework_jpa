package kr.ac.hansung.cse.exception;

public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException(String name) {
        super("이미 존재하는 카테고리입니다: " + name);
    }
}