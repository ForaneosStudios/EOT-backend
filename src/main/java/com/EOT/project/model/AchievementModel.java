package com.EOT.project.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "achievements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementModel extends AuditableModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ach_id", updatable = false, nullable = false)
    private UUID achId;

    @Column(name = "ach_name", nullable = false, unique = true, length = 100)
    private String achName;

    @Column(name = "ach_description", nullable = false, length = 500)
    private String achDescription;

    @Column(name = "ach_picture", length = 255)
    private String achPicture;

    @Column(name = "is_ach_hidden", nullable = false)
    @Builder.Default
    private Boolean isAchHidden = false;

    @OneToMany(mappedBy = "achievement", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<UserAchievementModel> userAchievements = new ArrayList<>();
}

