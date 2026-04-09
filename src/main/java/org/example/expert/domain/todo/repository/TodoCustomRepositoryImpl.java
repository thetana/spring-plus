package org.example.expert.domain.todo.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.expert.domain.todo.dto.response.TodoSearchResponse;
import org.example.expert.domain.todo.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.example.expert.domain.todo.entity.QTodo.todo;
import static org.example.expert.domain.user.entity.QUser.user;
import static org.example.expert.domain.manager.entity.QManager.manager;
import static org.example.expert.domain.comment.entity.QComment.comment;

@RequiredArgsConstructor
public class TodoCustomRepositoryImpl implements TodoCustomRepository {
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<Todo> findByIdWithUser(Long todoId) {
        Todo result = jpaQueryFactory.selectFrom(todo).leftJoin(todo.user, user).fetchJoin().where(todo.id.eq(todoId)).fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Page<TodoSearchResponse> search(
            String title, String nickname, LocalDateTime sdt, LocalDateTime edt, Pageable pageable) {

        List<TodoSearchResponse> content = jpaQueryFactory
                .select(
                        Projections.constructor(
                                TodoSearchResponse.class,
                                todo.id,
                                todo.title,
                                manager.id.countDistinct(),
                                comment.id.countDistinct()
                        )
                )
                .from(todo)
                .leftJoin(todo.managers, manager)
                .leftJoin(manager.user, user)
                .leftJoin(todo.comments, comment)
                .where(
                        hasTitle(title),
                        hasNickname(nickname),
                        createdAtBetween(sdt, edt)
                )
                .groupBy(todo.id, todo.title)
                .orderBy(todo.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long totalCount = jpaQueryFactory
                .select(todo.id.countDistinct())
                .from(todo)
                .leftJoin(todo.managers, manager)
                .leftJoin(manager.user, user)
                .where(
                        hasTitle(title),
                        hasNickname(nickname),
                        createdAtBetween(sdt, edt)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, totalCount != null ? totalCount : 0);
    }

    private BooleanExpression createdAtBetween(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null) {
            return todo.createdAt.between(startAt, endAt);
        } else if (startAt != null) {
            return todo.createdAt.after(startAt);
        } else if (endAt != null) {
            return todo.createdAt.before(endAt);
        } else {
            return null;
        }
    }

    private BooleanExpression hasNickname(String keyword) {
        return keyword != null ? user.nickname.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression hasTitle(String keyword) {
        return keyword != null ? todo.title.containsIgnoreCase(keyword) : null;
    }

}