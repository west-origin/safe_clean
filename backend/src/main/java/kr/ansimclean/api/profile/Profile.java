package kr.ansimclean.api.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

// 고객·매니저 공통 계정 정보(역할·이름·닉네임·휴대폰·가입 완료·탈퇴). 매니저 업무 정보는 ManagerProfile 에 따로 있다.
// 행은 가입 1단계(AuthService → ProfileService.createForSignup)에서 만들고, 쓰기·잠금은 ProfileService 를 거친다.
// 탈퇴해도 행은 지우지 않고 익명화한다 — 예약·정산이 이 id 를 가리킨다.
/** profiles — 계정 하나. id = Supabase auth.users.id. 상태 변화는 메서드로만 (CLAUDE.md §11-6) */
@Entity
@Table(name = "profiles")
// @DynamicUpdate: UPDATE 문에 바뀐 칸만 싣는다(기본은 모든 칸). 잠그지 않고 읽어 둔 옛 객체를 저장하는 경로가 생겨도
// 그 요청이 바꾸지 않은 이름·번호·탈퇴 표시(deleted_at)를 옛 값으로 덮어쓰지 않게 하는 두 번째 방어다 — 첫 방어는 탈퇴와 줄을 서는 잠금(문제 목록 28번)
@DynamicUpdate
public class Profile {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String role; // "customer" 또는 "manager" (Roles 상수)

    @Column(columnDefinition = "text", nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String nickname; // 고객만 쓴다 — 매니저 가입은 닉네임을 버린다 (AuthService)

    @Column(columnDefinition = "text", nullable = false)
    private String phone; // 로그인 휴대폰 번호(E.164). 살아 있는 계정끼리만 겹칠 수 없다(DB 부분 유니크 인덱스). 탈퇴하면 "deleted-<id>" 로 바꾼다

    @Column(name = "avatar_path", columnDefinition = "text")
    private String avatarPath; // 프로필 이미지의 Storage 경로. 탈퇴 때 비운다

    @Column(name = "signup_completed_at")
    private Instant signupCompletedAt; // 가입 마지막 단계를 마친 시각. null 이면 가입 도중

    @Column(name = "deleted_at")
    private Instant deletedAt; // 탈퇴 시각. null 이 아니면 탈퇴한 계정

    /** 탈퇴 + 5년 뒤 남은 개인정보를 파기한 시각 (V040, DeletedAccountPurger) */
    @Column(name = "purged_at")
    private Instant purgedAt;

    // insertable/updatable = false → JPA 는 이 두 칸을 쓰지 않는다. DB 가 채운다 (created_at 기본값 now(), updated_at 은 갱신 트리거)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** JPA 가 DB 행을 읽어 객체를 만들 때 쓰는 빈 생성자. 코드에서는 create 를 쓴다 */
    protected Profile() {}

    /** 가입 1단계의 새 계정 (가입 미완료). id 는 Supabase 사용자 id. 저장은 ProfileService.createForSignup 이 한다 */
    public static Profile create(UUID id, String role, String name, String nickname, String phone) {
        Profile p = new Profile();
        p.id = id;
        p.role = role;
        p.name = name;
        p.nickname = nickname;
        p.phone = phone;
        return p;
    }

    // 읽기용 getter
    public UUID getId() { return id; }
    public String getRole() { return role; }
    public String getName() { return name; }
    public String getNickname() { return nickname; }
    public String getPhone() { return phone; }
    public String getAvatarPath() { return avatarPath; }
    public Instant getSignupCompletedAt() { return signupCompletedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getPurgedAt() { return purgedAt; }

    /** 가입 마지막 단계까지 마쳤는가 (아니면 GET /auth/me 가 signupStep 에 다음 가입 화면을 싣는다) */
    public boolean isSignupComplete() {
        return signupCompletedAt != null;
    }

    /** 탈퇴한 계정인가 */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 가입 완료 시각을 적는다. 이미 적혀 있으면 처음 시각을 그대로 둔다 (두 번 불러도 같다) */
    public void completeSignup(Instant now) {
        if (signupCompletedAt == null) signupCompletedAt = now;
    }

    /** 내 정보 수정(ProfileService.updateProfile): 이름은 바꾸고, 닉네임은 null 이 아닐 때만 바꾼다 (null = 그대로) */
    public void rename(String name, String nickname) {
        this.name = name;
        if (nickname != null) this.nickname = nickname;
    }

    /** 탈퇴: 개인정보를 지우고 행은 남긴다(예약·정산 이력 보존). 5년 뒤 남은 개인정보는 DeletedAccountPurger 가 파기한다(markPurged) */
    public void anonymize(Instant now) {
        // 부르는 곳: ProfileService.anonymize (AuthService.deleteAccount 의 탈퇴 트랜잭션 안)
        this.name = "탈퇴한 사용자";
        this.nickname = null;
        this.phone = "deleted-" + id;
        this.avatarPath = null;
        this.deletedAt = now;
    }

    /** 탈퇴 + 5년 파기를 마쳤다 — 행은 예약·정산이 가리키므로 남긴다(이름·번호는 이미 익명) */
    public void markPurged(Instant now) {
        // 부르는 곳: ProfileService.markPurged. 탈퇴하지 않은 계정이면 예외로 멈춘다 — 살아 있는 계정을 파기 완료로 적지 않게
        if (deletedAt == null) throw new IllegalStateException("탈퇴하지 않은 계정은 파기하지 않는다");
        this.purgedAt = now;
    }
}
