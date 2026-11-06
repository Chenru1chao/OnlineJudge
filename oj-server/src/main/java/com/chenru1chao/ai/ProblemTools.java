package com.chenru1chao.ai;

import com.chenru1chao.dto.ProblemPageDTO;
import com.chenru1chao.entity.UserAccept;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.service.IUserAcceptService;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 暴露给模型的题库查询工具（Spring AI Function Calling）。
 *
 * <p>模型先调这个工具拿到真实题目，再基于返回结果推荐，
 * 从根上避免编造不存在的题目 id。用户已通过的题会被自动排除。</p>
 */
@Component
@RequiredArgsConstructor
public class ProblemTools {

    private static final int MAX_RESULTS = 20;
    private static final int FETCH_SIZE = 100;
    private static final String USER_ID_KEY = "userId";

    private final IProblemService problemService;
    private final IUserAcceptService userAcceptService;

    @Tool(name = "searchProblems",
            description = "按关键词、难度、标签搜索题库，优先返回当前用户还没通过的题目。"
                    + "当用户说想练某类题（如“动态规划”“图论”“递归”“二分”）时，"
                    + "这类“算法类型/题型”必须填进 tag 参数按标签搜索，不要当成标题关键词。"
                    + "本题库现有标签：输入输出、数学、分支、字符串、模拟、数论、循环、数组、动态规划、排序、二分、递归、前缀和、哈希。"
                    + "推荐时只能使用这里返回的题目，禁止编造。")
    public String searchProblems(
            @ToolParam(description = "题目标题关键词，仅当用户指定了题目名字时才填，可为空字符串", required = false) String keyword,
            @ToolParam(description = "算法类型/标签，只能从题库现有标签里选（如 动态规划、图论、递归）；用户说的“题型”填这里", required = false) String tag,
            @ToolParam(description = "难度，只能填 简单/中等/困难/不限", required = false) String difficulty,
            ToolContext toolContext) {

        Integer userId = userIdOf(toolContext);
        List<ProblemVO> problems = search(keyword, difficulty, tag, userId);
        String note = "";

        if (problems.isEmpty()) {
            // 排除了已通过之后为空？先看看是不是“匹配的题用户全通过了”：
            // 这时把已通过的那些题当复习候选返回，而不是直接说没有题。
            List<ProblemVO> solved = search(keyword, difficulty, tag, null);
            if (!solved.isEmpty()) {
                problems = solved;
                note = "以下题目用户都已经通过过了，只能作为复习候选。请明确告诉用户：没有未通过的新题了，这些是复习题。\n";
            }
        }
        if (problems.isEmpty()) {
            // 题库不大，严格条件查不到时逐级放宽，避免“动态规划”这种库里没有的标签直接卡死
            problems = search(keyword, null, null, userId);
            note = "没有完全匹配的题目，以下是放宽难度/标签后的结果，请如实告诉用户。\n";
        }
        if (problems.isEmpty()) {
            problems = search(null, null, null, userId);
            note = "没有找到匹配的题目，以下是题库里的部分题目，请如实告诉用户。\n";
        }
        if (problems.isEmpty()) {
            return "题库里没有可推荐的题目。请直接告诉用户稍后再试，不要编造题目。";
        }

        StringBuilder sb = new StringBuilder(note).append("可推荐的题目（推荐时只能从这里选）：\n");
        for (ProblemVO p : problems) {
            sb.append('#').append(p.getId()).append(' ')
                    .append(p.getTitle())
                    .append("（难度：").append(difficultyName(p.getDifficulty()))
                    .append("，通过率：").append(rate(p.getSubmitTotal(), p.getPassTotal()));
            if (p.getTags() != null && !p.getTags().isEmpty()) {
                sb.append("，标签：").append(String.join("/", p.getTags()));
            }
            sb.append("）\n");
        }
        sb.append("共 ").append(problems.size()).append(" 道。");
        return sb.toString();
    }

    /**
     * 供工具和降级逻辑共用：查询 + 排除已通过 + 最多 MAX_RESULTS 道。
     */
    public List<ProblemVO> search(String keyword, String difficulty, String tag, Integer userId) {
        ProblemPageDTO dto = new ProblemPageDTO();
        dto.setPageNO(1);
        dto.setPageSize(FETCH_SIZE);
        dto.setTitle(blankToNull(keyword));
        dto.setTag(blankToNull(tag));
        dto.setDifficulty(difficultyCode(difficulty));

        Result<PageResult<ProblemVO>> result = problemService.getProblemPage(dto);
        if (result.getData() == null || result.getData().getData() == null) {
            return List.of();
        }

        Set<Integer> accepted = acceptedProblemIds(userId);
        return result.getData().getData().stream()
                .filter(p -> !accepted.contains(p.getId()))
                .limit(MAX_RESULTS)
                .toList();
    }

    private Set<Integer> acceptedProblemIds(Integer userId) {
        if (userId == null) {
            return Set.of();
        }
        return userAcceptService.lambdaQuery()
                .eq(UserAccept::getUserId, userId)
                .list()
                .stream()
                .map(UserAccept::getProblemId)
                .collect(Collectors.toSet());
    }

    private static Integer userIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(USER_ID_KEY);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Integer difficultyCode(String difficulty) {
        if (difficulty == null) {
            return null;
        }
        if (difficulty.contains("简单") || difficulty.contains("入门")) {
            return 1;
        }
        if (difficulty.contains("中等") || difficulty.contains("进阶")) {
            return 2;
        }
        if (difficulty.contains("困难") || difficulty.contains("高难")) {
            return 3;
        }
        return null;
    }

    private static String difficultyName(Integer difficulty) {
        if (difficulty == null) {
            return "未知";
        }
        return switch (difficulty) {
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            default -> "未知";
        };
    }

    private static String rate(Integer submitTotal, Integer passTotal) {
        if (submitTotal == null || submitTotal == 0) {
            return "暂无";
        }
        int pass = passTotal == null ? 0 : passTotal;
        return Math.round(pass * 100.0 / submitTotal) + "%";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
