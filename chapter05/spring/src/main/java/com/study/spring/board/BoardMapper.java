package com.study.spring.board;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BoardMapper {

    List<Board> findAll();

    Board findById(Long id);

    void save(Board board);

    void update(@Param("id") Long id, @Param("board") Board board);

    void delete(Long id);
}
