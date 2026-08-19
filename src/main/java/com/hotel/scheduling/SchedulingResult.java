package com.hotel.scheduling;

import java.util.ArrayList;
import java.util.List;

/**
 * 排班结果：所有分配 + 所有问题
 */
public class SchedulingResult {

    /** 所有分配 */
    private final List<Assignment> assignments = new ArrayList<>();

    /** 缺人问题（硬性冲突） */
    private final List<String> shortageProblems = new ArrayList<>();

    /** 软性目标未达成的问题（希望休未满足、工时超等） */
    private final List<String> softProblems = new ArrayList<>();

    public void addAssignment(Assignment a) {
        assignments.add(a);
    }

    public void addShortageProblem(String msg) {
        shortageProblems.add(msg);
    }

    public void addSoftProblem(String msg) {
        softProblems.add(msg);
    }

    public List<Assignment> getAssignments() { return assignments; }
    public List<String> getShortageProblems() { return shortageProblems; }
    public List<String> getSoftProblems() { return softProblems; }

    public boolean hasProblems() {
        return !shortageProblems.isEmpty() || !softProblems.isEmpty();
    }

    /** 汇总打印 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("排班分配数：").append(assignments.size()).append("\n");
        sb.append("缺人问题数：").append(shortageProblems.size()).append("\n");
        sb.append("软性问题数：").append(softProblems.size()).append("\n");
        return sb.toString();
    }
}