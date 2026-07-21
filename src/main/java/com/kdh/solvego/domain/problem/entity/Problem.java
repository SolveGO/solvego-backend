package com.kdh.solvego.domain.problem.entity;

import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.converter.PositionListJsonConverter;
import com.kdh.solvego.domain.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,length=50)
    private String title;

    @Column(length = 100)
    private String description;

    @Convert(converter = PositionListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<Position> blackStones;

    @Convert(converter = PositionListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<Position> whiteStones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerColor nextPlayer;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "x",
                    column = @Column(name = "answer_x", nullable = false)
            ),
            @AttributeOverride(
                    name = "y",
                    column = @Column(name = "answer_y", nullable = false)
            )
    })
    private Position answerPosition;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Problem() {}

    public Problem(
            String title,
            String description,
            List<Position> blackStones,
            List<Position> whiteStones,
            PlayerColor nextPlayer,
            Position answerPosition,
            User creator
    ){
        this.title = title;
        this.description = description;
        this.blackStones = blackStones;
        this.whiteStones = whiteStones;
        this.nextPlayer = nextPlayer;
        this.answerPosition = answerPosition;
        this.creator=creator;
    }

    public boolean isCorrectPosition(Position selectedPosition) {
        return this.answerPosition.equals(selectedPosition);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getCreator(){
        return creator;
    }

    public List<Position> getBlackStones() {
        return blackStones;
    }

    public List<Position> getWhiteStones() {
        return whiteStones;
    }

    public PlayerColor getNextPlayer() {
        return nextPlayer;
    }

    public Position getAnswerPosition() {
        return answerPosition;
    }

    public void update(
            String title,
            String description,
            List<Position> blackStones,
            List<Position> whiteStones,
            PlayerColor nextPlayer,
            Position answerPosition
    ) {
        this.title = title;
        this.description = description;
        this.blackStones = blackStones;
        this.whiteStones = whiteStones;
        this.nextPlayer = nextPlayer;
        this.answerPosition = answerPosition;
    }
}
