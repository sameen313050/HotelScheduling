package com.hotel.scheduling;

import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import java.time.LocalDate;

/**
 * 排班任务：某天某设施某班次需要几人
 * シフトタスク：ある日のある施設・シフトで必要な人数
 * Scheduling task: required headcount for a facility/shift on a given day
 *
 * <p>算法把"需求"转化为"任务"，然后一个一个尝试分配员工。
 * 每填一个人 → assignedCount++，直到 isFull() 为 true。</p>
 *
 * <p>アルゴリズムは「需要」を「タスク」に変換し、1 件ずつ従業員を割り当てる。
 * 1 人配置するたびに assignedCount++、isFull() が true になるまで続ける。</p>
 *
 * <p>The algorithm converts "demand" into "tasks" and assigns employees one by one.
 * Each assignment increments assignedCount++ until isFull() becomes true.</p>
 *
 * <p>示例：2026-08-15 東京ホテル 早番 需要 2 人 → 排到 1 人时 shortage=1，排到 2 人时 isFull=true</p>
 * <p>例：2026-08-15 東京ホテル 早番 2 人必要 → 1 人配置で shortage=1、2 人配置で isFull=true</p>
 * <p>Example: 2026-08-15 Tokyo Hotel Morning needs 2 → after 1 assignment shortage=1, after 2 isFull=true</p>
 */
public class Task {

    // 日期（final，任务一旦创建不可更改）
    // 日付（final、タスク作成後は変更不可）
    // Date (final, immutable once task is created)
    private final LocalDate date;

    // 设施 / 施設 / Facility
    private final Facility facility;

    // 班次 / シフト種別 / Shift type
    private final ShiftType shiftType;

    // 需要人数（根据稼働率/CI/CO 从需求配置算出）
    // 必要人数（稼働率/CI/CO に基づき需要設定から算出）
    // Required headcount (computed from demand rules based on occupancy/CI/CO)
    private final int requiredCount;

    /**
     * 已分配到这个任务的人数（算法执行中累加）
     * 当該タスクに割当済みの人数（アルゴリズム実行中に累積）
     * Number of employees already assigned (accumulated during algorithm execution)
     */
    private int assignedCount;

    /**
     * 构造方法：初始化任务，已分配人数从 0 开始
     * コンストラクタ：タスクを初期化、割当人数は 0 から開始
     * Constructor: initialize task with assignedCount starting at 0
     */
    public Task(LocalDate date, Facility facility, ShiftType shiftType, int requiredCount) {
        this.date = date;
        this.facility = facility;
        this.shiftType = shiftType;
        this.requiredCount = requiredCount;
        this.assignedCount = 0;
    }

    // ===== Getter =====

    public LocalDate getDate() { return date; }
    public Facility getFacility() { return facility; }
    public ShiftType getShiftType() { return shiftType; }
    public int getRequiredCount() { return requiredCount; }
    public int getAssignedCount() { return assignedCount; }

    /**
     * 已分配人数 +1（算法分配成功时调用）
     * 割当人数 +1（アルゴリズム割当成功時に呼ぶ）
     * Increment assigned count by 1 (called upon successful assignment)
     */
    public void incrementAssigned() { this.assignedCount++; }

    /**
     * 还差几人才能排满
     * あと何人で充足するか
     * How many more employees are needed to fill this task
     */
    public int getShortage() { return requiredCount - assignedCount; }

    /**
     * 是否已排满（算法主循环的终止条件）
     * 充足済みか（アルゴリズムメインループの終了条件）
     * Whether the task is fully staffed (termination condition of main loop)
     */
    public boolean isFull() { return assignedCount >= requiredCount; }

    /**
     * 显示用：便于调试
     * 表示用：デバッグに便利
     * For display: convenient for debugging
     *
     * 例：2026-08-15 東京ホテル 早番 (1/2)
     * Example: 2026-08-15 Tokyo Hotel Morning (1/2)
     */
    @Override
    public String toString() {
        return date + " " + facility.getName() + " " + shiftType.getName()
                + " (" + assignedCount + "/" + requiredCount + ")";
    }
}