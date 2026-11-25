package com.hotel.scheduling;

import java.util.ArrayList;
import java.util.List;

/**
 * 排班结果：所有分配 + 所有问题
 * シフト結果：全割当 + 全問題リスト
 * Scheduling result: all assignments plus all problems
 *
 * <p>算法执行完成后，所有信息（成功的分配、硬性缺人、软性未满足）都封装在这里，
 * 由 UI 层取出展示给用户。</p>
 *
 * <p>アルゴリズム完了後、全情報（成功した割当、ハード人手不足、ソフト未達）を
 * ここにまとめ、UI 層が取り出してユーザに表示する。</p>
 *
 * <p>After the algorithm completes, all information (successful assignments,
 * hard shortages, unmet soft goals) is packaged here for the UI layer to present.</p>
 */
public class SchedulingResult {

    /**
     * 所有成功的分配
     * 成功した全割当
     * All successful assignments
     */
    private final List<Assignment> assignments = new ArrayList<>();

    /**
     * 缺人问题（硬性冲突）：某日某班次人数不足
     * 人手不足問題（ハード競合）：ある日のあるシフトで人員が足りない
     * Staff shortage problems (hard conflicts): insufficient headcount on a given day/shift
     */
    private final List<String> shortageProblems = new ArrayList<>();

    /**
     * 软性目标未达成的问题（希望休未满足、工时超等）
     * ソフト目標未達の問題（希望休未達、勤務時間超過等）
     * Unmet soft goals (preferred day-off not honored, hours over target, etc.)
     */
    private final List<String> softProblems = new ArrayList<>();

    // ===== 添加方法（供算法在执行过程中调用）=====
    // ===== 追加メソッド（アルゴリズム実行中に呼び出される）=====
    // ===== Add methods (called by the algorithm during execution) =====

    public void addAssignment(Assignment a) {
        assignments.add(a);
    }

    public void addShortageProblem(String msg) {
        shortageProblems.add(msg);
    }

    public void addSoftProblem(String msg) {
        softProblems.add(msg);
    }

    // ===== Getter =====

    public List<Assignment> getAssignments() { return assignments; }
    public List<String> getShortageProblems() { return shortageProblems; }
    public List<String> getSoftProblems() { return softProblems; }

    /**
     * 是否存在任何问题（硬性 or 软性）
     * 問題の有無（ハード or ソフト）
     * Whether any problem exists (hard or soft)
     */
    public boolean hasProblems() {
        return !shortageProblems.isEmpty() || !softProblems.isEmpty();
    }

    /**
     * 汇总打印（用于日志或调试）
     * サマリー出力（ログ・デバッグ用）
     * Summary output (for logging or debugging)
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("シフト割当件数：").append(assignments.size()).append("\n");
        sb.append("人手不足件数：").append(shortageProblems.size()).append("\n");
        sb.append("ソフト問題件数：").append(softProblems.size()).append("\n");
        return sb.toString();
    }
}