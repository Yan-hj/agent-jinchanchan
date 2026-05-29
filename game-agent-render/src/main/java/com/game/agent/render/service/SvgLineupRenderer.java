package com.game.agent.render.service;

import com.game.agent.render.model.*;
import java.util.*;

/**
 * SVGs 阵容棋盘渲染器。
 * <p>
 * 将结构化的 Board 数据模型渲染为深色主题的 SVG 棋盘图。
 * 棋盘规格：4行 × 7列，每个格子 88×76 像素。
 * <p>
 * 布局：
 * ┌──────────────────────────────────────────────┐
 * │  棋盘 (4×7 网格)                               │ 羁绊列表  │
 * │  ┌────┬────┬────┬────┬────┬────┬────┐        │          │
 * │  │ ★  │    │ ★  │    │    │    │    │        │ 7法师    │
 * │  │安妮 │    │维迦│    │    │    │    │        │ 2神谕    │
 * │  │ Ⅲ  │    │ Ⅱ  │    │    │    │    │        │          │
 * │  └────┴────┴────┴────┴────┴────┴────┘        │ 装备池   │
 * │                                               │ 大棒 眼泪 │
 * │ 等级 8  经验 6/10              金币 42        │ 青龙刀   │
 * │  备战席: [阿狸] [慎]                          │          │
 * └──────────────────────────────────────────────┘
 * <p>
 * 英雄按费用着色：1费银、2费绿、3费蓝、4费紫、5费金。
 * 装备用 2 字缩写 + 颜色区分类型。
 */
public class SvgLineupRenderer {

    private static final int CELL_W = 88;          // 每个格子的宽度
    private static final int CELL_H = 76;          // 每个格子的高度
    private static final int GAP = 6;              // 格子间距
    private static final int BOARD_X = 12;         // 棋盘左上角 X
    private static final int BOARD_Y = 12;         // 棋盘左上角 Y
    private static final int ROWS = 4;
    private static final int COLS = 7;
    private static final int BOARD_W = COLS * CELL_W + (COLS - 1) * GAP;
    private static final int BOARD_H = ROWS * CELL_H + (ROWS - 1) * GAP;
    private static final int CANVAS_W = 760;       // 画布总宽度
    private static final int CANVAS_H = 540;       // 画布总高度

    // 各费用对应的边框颜色
    private static final Map<Integer, String> COST_COLORS = Map.of(
            1, "#9ea7ae", 2, "#2ecc71", 3, "#3498db", 4, "#9b59b6", 5, "#f1c40f"
    );

    // 费用中文标签
    private static final Map<Integer, String> COST_NAMES = Map.of(
            1, "1费", 2, "2费", 3, "3费", 4, "4费", 5, "5费"
    );

    /**
     * 渲染完整的阵容 SVG 字符串。
     *
     * @param board 阵容数据模型
     * @return 可直接在前端展示的 SVG 标记
     */
    public String render(Board board) {
        StringBuilder svg = new StringBuilder();
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" " +
                "style=\"font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif; background: #1a1a2e;\">\n",
                CANVAS_W, CANVAS_H));

        appendDefs(svg);
        appendHeader(svg, board);
        appendBoardGrid(svg, board);
        appendBench(svg, board);
        appendRightPanel(svg, board);

        svg.append("</svg>");
        return svg.toString();
    }

    /** 定义滤镜和渐变 */
    private void appendDefs(StringBuilder svg) {
        svg.append("<defs>\n");
        svg.append("  <filter id=\"glow\"><feGaussianBlur stdDeviation=\"2\" result=\"blur\"/>" +
                "<feMerge><feMergeNode in=\"blur\"/><feMergeNode in=\"SourceGraphic\"/></feMerge></filter>\n");
        svg.append("  <linearGradient id=\"starGold\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">" +
                "<stop offset=\"0%\" stop-color=\"#ffd700\"/>" +
                "<stop offset=\"100%\" stop-color=\"#ff8c00\"/></linearGradient>\n");
        svg.append("</defs>\n");
    }

    /** 顶部信息栏：等级、经验、金币 */
    private void appendHeader(StringBuilder svg, Board board) {
        int headerY = BOARD_Y + BOARD_H + GAP * 2;
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" fill=\"#ffd700\" font-size=\"18\" font-weight=\"bold\">" +
                "等级 %d</text>\n", BOARD_X, headerY + 4, board.level()));
        if (board.experience() != null) {
            svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#aaa\" font-size=\"13\">经验 %s</text>\n",
                    BOARD_X + 80, headerY + 4, board.experience()));
        }
        svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#f1c40f\" font-size=\"13\">金币 %d</text>\n",
                CANVAS_W - 140, headerY + 4, board.gold()));
    }

    /** 渲染 4×7 棋盘格子 */
    private void appendBoardGrid(StringBuilder svg, Board board) {
        Map<String, Champion> posMap = new HashMap<>();
        for (Champion c : board.champions()) {
            posMap.put(c.row() + "," + c.col(), c);
        }

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = BOARD_X + col * (CELL_W + GAP);
                int y = BOARD_Y + row * (CELL_H + GAP);
                Champion champ = posMap.get(row + "," + col);

                if (champ != null) {
                    appendChampionCell(svg, x, y, champ);
                } else {
                    appendEmptyCell(svg, x, y);
                }
            }
        }
    }

    /** 渲染有英雄的格子：背景色 → 费用标签 → 姓名 → 星级 → 装备 */
    private void appendChampionCell(StringBuilder svg, int x, int y, Champion champ) {
        String color = COST_COLORS.getOrDefault(champ.cost(), "#555");
        int cx = x + CELL_W / 2;

        svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"6\" " +
                "fill=\"%s\" opacity=\"0.25\" filter=\"url(#glow)\"/>\n", x, y, CELL_W, CELL_H, color));
        svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"6\" " +
                "fill=\"%s\" opacity=\"0.6\" stroke=\"%s\" stroke-width=\"1.5\"/>\n",
                x, y, CELL_W, CELL_H, color, color));

        svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#fff\" font-size=\"10\" text-anchor=\"end\">%s</text>\n",
                x + CELL_W - 5, y + 14, costLabel(champ.cost())));

        svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#fff\" font-size=\"13\" font-weight=\"bold\" text-anchor=\"middle\">%s</text>\n",
                cx, y + 28, champ.name()));

        String stars = "★".repeat(Math.min(champ.star(), 3));
        svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"url(#starGold)\" font-size=\"12\" text-anchor=\"middle\">%s</text>\n",
                cx, y + 44, stars));

        // 装备：最多显示 3 件，每个 18×18 色块 + 2字缩写
        if (champ.items() != null && !champ.items().isEmpty()) {
            int itemStartX = cx - (champ.items().size() * 22) / 2;
            for (int i = 0; i < Math.min(champ.items().size(), 3); i++) {
                int ix = itemStartX + i * 22;
                String itemColor = itemColor(champ.items().get(i));
                svg.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"18\" height=\"18\" rx=\"3\" " +
                        "fill=\"%s\" stroke=\"#fff\" stroke-width=\"0.5\"/>\n", ix, y + 52, itemColor));
                svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#fff\" font-size=\"9\" text-anchor=\"middle\">%s</text>\n",
                        ix + 9, y + 65, itemAbbr(champ.items().get(i))));
            }
        }
    }

    /** 空格子：虚线边框 */
    private void appendEmptyCell(StringBuilder svg, int x, int y) {
        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"6\" " +
                "fill=\"#2a2a3e\" stroke=\"#444\" stroke-width=\"1\" stroke-dasharray=\"4,4\"/>\n",
                x, y, CELL_W, CELL_H));
    }

    /** 渲染备战席（最多 9 个格子） */
    private void appendBench(StringBuilder svg, Board board) {
        if (board.bench() == null || board.bench().isEmpty()) return;

        int benchY = BOARD_Y + BOARD_H + GAP * 4 + 24;
        svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#888\" font-size=\"12\">备战席</text>\n",
                BOARD_X, benchY));

        int bx = BOARD_X;
        int bw = 68;
        int bh = 56;
        for (int i = 0; i < Math.min(board.bench().size(), 9); i++) {
            Champion c = board.bench().get(i);
            String bColor = COST_COLORS.getOrDefault(c.cost(), "#555");
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"4\" " +
                    "fill=\"%s\" opacity=\"0.4\" stroke=\"%s\" stroke-width=\"0.8\"/>\n",
                    bx, benchY + 16, bw, bh, bColor, bColor));
            svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#fff\" font-size=\"11\" text-anchor=\"middle\">%s</text>\n",
                    bx + bw / 2, benchY + 36, c.name()));
            String stars = "★".repeat(Math.min(c.star(), 3));
            svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#ffd700\" font-size=\"10\" text-anchor=\"middle\">%s</text>\n",
                    bx + bw / 2, benchY + 52, stars));
            bx += bw + GAP;
        }
    }

    /** 右侧信息面板：羁绊列表 + 装备池 */
    private void appendRightPanel(StringBuilder svg, Board board) {
        int px = BOARD_X + BOARD_W + 24;
        int py = BOARD_Y + 4;
        int lineH = 22;

        svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#ffd700\" font-size=\"14\" font-weight=\"bold\">羁绊</text>\n",
                px, py));
        py += lineH;
        if (board.activeSynergies() != null) {
            for (ActiveSynergy s : board.activeSynergies()) {
                svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#e0e0e0\" font-size=\"12\">%s</text>\n",
                        px, py, s.name()));
                if (s.description() != null) {
                    svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#888\" font-size=\"10\">%s</text>\n",
                            px, py + 14, s.description()));
                    py += lineH;
                }
                py += lineH;
            }
        }

        py += 12;
        if (board.itemPool() != null) {
            svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#ffd700\" font-size=\"14\" font-weight=\"bold\">装备池</text>\n",
                    px, py));
            py += lineH;
            ItemPool pool = board.itemPool();
            if (pool.components() != null && !pool.components().isEmpty()) {
                svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#aaa\" font-size=\"11\">散件:</text>\n", px, py));
                svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#e0e0e0\" font-size=\"11\">%s</text>\n",
                        px + 40, py, String.join(" ", pool.components())));
                py += lineH;
            }
            if (pool.completed() != null && !pool.completed().isEmpty()) {
                svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#aaa\" font-size=\"11\">成装:</text>\n", px, py));
                svg.append(String.format("<text x=\"%d\" y=\"%d\" fill=\"#ffd700\" font-size=\"11\">%s</text>\n",
                        px + 40, py, String.join(" ", pool.completed())));
            }
        }
    }

    private String costLabel(int cost) {
        return COST_NAMES.getOrDefault(cost, cost + "费");
    }

    /** 根据装备名称首字判断颜色 */
    private String itemColor(String itemName) {
        if (itemName.isEmpty()) return "#7f8c8d";
        return switch (itemName.charAt(0)) {
            case '青', '蓝', '眼', '女', '大' -> "#3498db";
            case '法', '帽', '科', '卢' -> "#9b59b6";
            case '反', '日', '龙', '狂' -> "#e74c3c";
            case '无', '正', '巨', '饮' -> "#e67e22";
            case '鬼', '红', '羊', '电' -> "#f1c40f";
            case '刺', '穿', '轻', '破' -> "#1abc9c";
            default -> "#7f8c8d";
        };
    }

    /** 装备名称截取前 2 字作为缩写 */
    private String itemAbbr(String itemName) {
        if (itemName.length() <= 2) return itemName;
        return itemName.substring(0, 2);
    }
}
