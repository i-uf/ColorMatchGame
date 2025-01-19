package com.i_uf;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Game extends JPanel {
    private static final Game INSTANCE = new Game();
    private final Canvas canvas = new Canvas();
    private static final Map<String, Image> images = new HashMap<>();
    private final Font big = new Font("Noto Serif CJK KR", Font.BOLD, 50);
    private static final Font small = new Font("Noto Serif CJK KR", Font.BOLD, 25);

    private boolean animation = true;
    private long prev = 0;
    private long accumulation = 0;
    private static final long animationTime = 200;

    private final LevelItem level = new LevelItem(new Level(10, "001000100011101110111111111111111111111111111011111110001111100000111000000010000"));
    private final Abilities abilities = new Abilities();
    private final List<Item> items = List.of(level, abilities);
    private int x = -1, y = -1;

    public static void main(String... ignored) {
        JFrame frame = new JFrame("Color Match");
        frame.setBackground(Color.BLACK);
        frame.setContentPane(INSTANCE);
        INSTANCE.setPreferredSize(new Dimension(500, 725));
        frame.setResizable(false);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        INSTANCE.init();
        frame.setVisible(true);
    }
    public void init() {
        MouseAdapter listener = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if(animation || e.getButton() != 1) return;
                x = e.getX() - 25;
                y = e.getY() - 125;
                if(x < 0 || y < 0 || !level.level.isValid(x/=50, y/=50) || level.level.isEmpty(x, y)) { x = -1; y = -1; }
            }
            public void mouseReleased(MouseEvent e) {
                if(animation || e.getButton() != 1) return;
                int x2 = (e.getX() - 25) / 50;
                int y2 = (e.getY() - 125) / 50;
                if(e.getX() < 25 || e.getY() < 125) return;
                if(level.level.canMoves().stream().anyMatch(m -> m.x1 == x && m.y1 == y && m.x2 == x2 && m.y2 == y2)) {
                    level.level.addSwap(x, y, x2, y2, level.level.get(x2, y2), level.level.get(x, y), level.level.get(x, y), level.level.get(x2, y2));
                    animation = true;
                    --level.level.remainMoves;
                }
                x = -1;
                y = -1;

            }
        };
        addMouseListener(listener);
        addMouseMotionListener(listener);
        addMouseWheelListener(listener);
        prev = System.currentTimeMillis();
        new Thread(() -> {
            while (true) {
                var time = System.currentTimeMillis() - prev;
                if(time < 3) continue;
                accumulation += animation ? time : -accumulation;
                prev = System.currentTimeMillis();
                if (items.stream().anyMatch(Item::update)) animation = true;
                if (animation) {
                    if (accumulation >= animationTime) animation = false;
                }
                SwingUtilities.invokeLater(this::repaint);
            }
        }).start();
    }
    public synchronized void paintComponent(Graphics g) {
        canvas.fill(0, 0, 500, 100, 0xFFFFFFFF)
                .text( "%d Moves".formatted(INSTANCE.level.level.remainMoves), 0, 0, 200, 100, 0xFF000000, 4, small);
        items.forEach(item -> item.render(canvas, 0, 0));
        canvas.graphics(g);
    }
    private static Image loadImage(String path) {
        if(images.containsKey(path)) return images.get(path);
        try {
            Image image = ImageIO.read(Game.class.getResourceAsStream(path));
            images.put(path, image);
            return image;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static class Canvas {
        private final LinkedList<Consumer<Graphics>> context = new LinkedList<>();

        public Canvas graphics(Graphics graphics) {
            while(!context.isEmpty()) context.pop().accept(graphics);
            return this;
        }

        Canvas image(int x, int y, int w, int h, int color, String image) {
            context.add(graphics->graphics.drawImage(loadImage(image), x, y, w, h, new Color(color, true), null));
            return this;
        }
        Canvas image(int x, int y, int w, int h, String image) {
            context.add(graphics->graphics.drawImage(loadImage(image), x, y, w, h, null));
            return this;
        }
        Canvas fill(int x, int y, int w, int h, int color) {
            context.add(graphics-> {
                graphics.setColor(new Color(color, true));
                graphics.fillRect(x, y, w, h);
            });
            return this;
        }
        Canvas text(String text, int x, int y, int w, int h, int color, int alignment, Font font) {
            context.add(graphics-> {
                String text1 = width(text, w, font);
                graphics.setColor(new Color(color, true));
                graphics.setFont(font);
                int modifiedY = y + switch (alignment / 3) {
                    case 1 -> (h - ascent(font) - descent(font)) / 2 + ascent(font);
                    case 2 -> h - descent(font);
                    default -> ascent(font);
                };
                for (String text2 : text1.split("\n")) {
                    graphics.drawString(
                            text2, x + switch (alignment % 3) {
                                case 1 -> (w - width(text2, font)) / 2;
                                case 2 -> w - width(text2, font);
                                default -> 0;
                            }, modifiedY);
                    modifiedY += h;
                }
            });
            return this;
        }

        private String width(String text, int width, Font font) {
            if (width(text, font) <= width || width == 0) return text;
            var index = 0;
            var total = width("..", font);
            while (index < text.length() && (total += width(text.charAt(index), font)) <= width) index++;
            return "%s..".formatted(text.substring(0, index));
        }
        public int width(char c, Font font) {
            return INSTANCE.getFontMetrics(font).charWidth(c);
        }
        public int width(String s, Font font) {
            return s.chars().map(it -> width((char) it, font) ).sum();
        }
        private int ascent(Font font) {
            return INSTANCE.getFontMetrics(font).getAscent();
        }

        private int descent(Font font) {
            return INSTANCE.getFontMetrics(font).getDescent();
        }
    }

    private interface Item {
        void render(Canvas canvas, int x, int y);
        default boolean update() { return false; }
    }
    private enum Type implements Item {
        INVALID(0), NONE(0),
        RED(0xFFFF0000), YELLOW(0xFFFFFF00),
        GREEN(0xFF00FF00), BLUE(0xFF0000FF),
        CRYSTAL(0xFFFFFFFF),
        H_ROCKET(0xFF00FFFF), V_ROCKET(0xFFFF00FF);
        private final int color;
        Type(int color) { this.color = color; }
        public void render(Canvas canvas, int x, int y) {
            canvas.fill(30 + x, 130 + y, 40, 40, color);
        }
    }
    private record Animation(int x1, int y1, int x2, int y2, Type type) {}
    private record Move(int x1, int y1, int x2, int y2) {}
    private record Match(int x, int y, Pattern pattern, Type origin) {}
    private record Position(int x, int y) {}
    private record LevelItem(Level level) implements Item {
        public void render(Canvas canvas, int ignore, int ignored) {
            for(int y = 0; y < 9; y++) {
                for(int x = 0; x < 9; x++) {
                    if(!level.isValid(x, y)) continue;
                    canvas.fill(25 + x * 50, 125 + y * 50, 50, 50, x == INSTANCE.x && y == INSTANCE.y ? 0xFFFFFFFF : 0xFF7F7F7F);
                    final int xx = x;
                    final int yy = y;
                    if(level.animations.stream().anyMatch(a -> a.x1 == xx && a.y1 == yy || a.x2 == xx && a.y2 == yy)) continue;
                    level.get(x, y).render(canvas, x * 50, y * 50);
                }
            }
            level.animations.forEach(a -> {
                a.type.render(canvas, (int) lerp(a.x1 * 50, a.x2 * 50, INSTANCE.accumulation*1.0/animationTime), (int) lerp(a.y1 * 50, a.y2 * 50, INSTANCE.accumulation*1.0/animationTime));
            });
        }
        public boolean update() {
            return level.update();
        }
        private double lerp(int a, int b, double t) {
            return a * (1.0 - t) + b * t;
        }
    }
    private static class Level {
        public final int moves;
        public int remainMoves;
        private final LinkedList<Animation> animations = new LinkedList<>();
        private final Type[] level = new Type[81];

        public Level(int moves, String text) {
            this.moves = moves;
            this.remainMoves = moves;
            for(int x = 0; x < 81; x ++) level[x] = Type.values()[text.charAt(x)-'0'];
        }
        public Level(Level level) {
            this.moves = level.moves;
            this.remainMoves = level.moves;
            System.arraycopy(level.level, 0, this.level, 0, 81);
        }
        public boolean update() {
            if(INSTANCE.animation) return true;
            animations.clear();
            return (spawn() | flow()) || match();
        }
        public boolean spawn() {
            boolean spawn = false;
            for(int x = 0; x < 9; x++) {
                Integer top = findTop(x, Type.NONE);
                var random = Type.values()[2+Math.abs(ThreadLocalRandom.current().nextInt()%4)];
                if(top != null) {
                    set(x, top, random);
                    spawn = true;
                }
            }
            return spawn;
        }
        public boolean flow() {
            boolean flow = false;
            for(int x = 0; x < 9; x++) {
                for(int y = 8; y >= -1; y--) {
                    if(get(x, y) == Type.INVALID) continue;
                    if(get(x, y+1) == Type.INVALID) continue;
                    if(isEmpty(x, y)) continue;
                    if(!isEmpty(x, y+1)) continue;
                    addSwap(x, y, x, y+1, Type.NONE, get(x, y), get(x, y), Type.NONE);
                    flow = true;
                }
            }
            return flow;
        }
        public LinkedList<Move> canMoves() {
            if(new Level(this).match()) return new LinkedList<>();
            LinkedList<Move> moves = new LinkedList<>();
            for(int x = 0; x < 9; x++) {
                for(int y = 0; y < 9; y++) {
                    if (move(x, y, x, y).match()) moves.add(new Move(x, y, x, y));
                    if (move(x, y, x+1, y).match()) moves.add(new Move(x, y, x+1, y));
                    if (move(x, y, x-1, y).match()) moves.add(new Move(x, y, x-1, y));
                    if (move(x, y, x, y+1).match()) moves.add(new Move(x, y, x, y+1));
                    if (move(x, y, x, y-1).match()) moves.add(new Move(x, y, x, y-1));
                }
            }
            return moves;
        }
        public Level move(int x1, int y1, int x2, int y2) {
            if(!isValid(x1, y1) || !isValid(x2, y2)) return new Level(this);
            Level copy = new Level(this);
            Type temp = copy.get(x1, y1);
            copy.set(x1, y1, copy.get(x2, y2));
            copy.set(x2, y2, temp);
            return copy;
        }
        public boolean match() {
            AtomicBoolean match = new AtomicBoolean(false);
            for(int x = 0; x < 9; x++) {
                for(int y = 0; y < 9; y++) {
                    if(!matchable(get(x, y))) continue;
                    Position position = new Position(x, y);
                    Type type = get(x, y);
                    Arrays.stream(Pattern.values()).sorted(Comparator.comparingInt(a -> a.priority)).forEach(a -> {
                        if(!Arrays.stream(a.positions).allMatch(p -> get(position.x + p.x, position.y + p.y) == type)) return;
                        Arrays.stream(a.positions).forEach(p -> addSwap(position.x + p.x, position.y + p.y, position.x, position.y, Type.NONE, a.type, type, Type.NONE));

                        addSet(position.x, position.y, a.type, type);
                        match.set(true);
                    });
                }
            }
            return match.get();
        }
        private boolean matchable(Type type) {
            return type == Type.RED || type == Type.YELLOW || type == Type.GREEN || type == Type.BLUE;
        }
        public Integer findTop(int x, Type type) {
            for(int y = 0; y < 9; y++) {
                if(get(x, y) == type) return y;
                if(isValid(x, y)) return null;
            }
            return null;
        }
        public Type get(int x, int y) {
            return checkPosition(x, y) ? level[y*9+x] : Type.INVALID;
        }
        public void set(int x, int y, Type type) {
            if(isValid(x, y)) level[y*9+x] = type;
        }
        public void addSet(int x, int y, Type type1, Type type2) {
            if(isValid(x, y)) animations.add(new Animation(x, y, x, y, type2));
            set(x, y, type1);
        }
        public void addSwap(int x1, int y1, int x2, int y2, Type type1, Type type2, Type type3, Type type4) {
            animations.add(new Animation(x1, y1, x2, y2, type3));
            animations.add(new Animation(x2, y2, x1, y1, type4));
            set(x1, y1, type1);
            set(x2, y2, type2);
        }
        public boolean isValid(int x, int y) {
            return get(x, y) != Type.INVALID;
        }
        public boolean isEmpty(int x, int y) {
            return get(x, y) == Type.NONE;
        }
        public boolean checkPosition(int x, int y) {
            return Math.min(x, y) >= 0 && Math.max(x, y) < 9;
        }
    }
    private enum Pattern {
        H_CRYSTAL(Type.CRYSTAL, 1, new Position(-2, 0), new Position(-1, 0), new Position(1, 0), new Position(2, 0)),
        V_CRYSTAL(Type.CRYSTAL, 2, new Position(0, -2), new Position(0, -1), new Position(0, 1), new Position(0, 2)),
        H_ROCKET(Type.H_ROCKET, 3, new Position(0, -1), new Position(0, 1), new Position(0, 2)),
        V_ROCKET(Type.V_ROCKET, 4, new Position(-1, 0), new Position(1, 0), new Position(2, 0)),
        H_EAT(Type.NONE, 5, new Position(-1, 0), new Position(1, 0)),
        V_EAT(Type.NONE, 6, new Position(0, -1), new Position(0, 1));
        public final Type type;
        public final int priority;
        public final Position[] positions;
        Pattern(Type type, int priority, Position... positions) { this.type = type; this.priority = priority; this.positions = positions; }
    }
    private enum Ability {
        HAMMER, H_LASER, V_LASER
    }
    private static class Abilities implements Item {
        public void render(Canvas canvas, int a, int b) {
            var abilitiesP = Ability.values().length + 1;
            for(int x = 0; x < abilitiesP; x++) {
            }
        }
    }
}
