package com.study.spring.board;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BoardDao {

    private final JdbcTemplate jdbcTemplate;

    public BoardDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Board> boardRowMapper = (rs, rowNum) -> new Board(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("writer"),
            rs.getTimestamp("created_at").toLocalDateTime());

    public List<Board> findAll() {
        String sql = "SELECT * FROM board ORDER BY id DESC";
        return jdbcTemplate.query(sql, boardRowMapper);
    }

    public Board findById(Long id) {
        String sql = "SELECT * FROM board WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, boardRowMapper, id);
    }

    public void save(Board board) {
        String sql = "INSERT INTO board(title, content, writer) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, board.getTitle(), board.getContent(), board.getWriter());
    }

    public void update(Long id, Board board) {
        String sql = "UPDATE board SET title = ?, content = ?, writer = ? WHERE id = ?";
        jdbcTemplate.update(sql, board.getTitle(), board.getContent(), board.getWriter(), id);
    }

    public void delete(Long id) {
        String sql = "DELETE FROM board WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}