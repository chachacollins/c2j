import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

enum TokenKind {
    LParen,
    RParen,
    Ident,
    LBrace,
    RBrace,
    Semicolon,
    String,
    Printf,
    Void,
    Eof,
    Illegal
}

class Token {
    public TokenKind kind;
    public String lit;

    public Token(TokenKind kind, String lit) {
        this.kind = kind;
        this.lit = lit;
    }
    @Override
    public String toString() {
        return "Token: " + "Kind = " + this.kind + ", lit = " + this.lit;
    }
}

class Lexer {
    private String src;
    private int read_pos;
    private int pos;

    public Lexer(String src) {
        this.src = src;
        this.read_pos = 0;
        this.pos = 0;
    }

    private char advance() {
        if(this.read_pos >= this.src.length()) {
            return '\0';
        }
        this.pos = this.read_pos;
        this.read_pos += 1;
        return this.src.charAt(this.pos);
    }

    private char peek() {
        if(this.read_pos >= this.src.length()) {
            return '\0';
        }
        return this.src.charAt(this.read_pos);
    }

    private void skip_whitespace() {
        while(true) {
            char c = this.peek();
            switch (c) {
                case '\n': case '\t': case ' ': case '\r': this.advance(); break;
                case '#' :  while(this.peek() != '\n') this.advance(); break;
                default  : return;
            }
        }
    }

    private boolean is_alpha(char c) {
        return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
    }

    public Token nextToken() {
        this.skip_whitespace();
        char c = this.advance();
        if(this.is_alpha(c)) {
            StringBuilder sb = new StringBuilder();
            sb.append(c);
            while(this.is_alpha(this.peek())) {
                sb.append(this.advance());
            }
            String ident = sb.toString();
            if ("void".equals(ident)) {
                return new Token(TokenKind.Void, ident);
            }  else if("printf".equals(ident)) {
                return new Token(TokenKind.Printf, ident);
            } else {
                return new Token(TokenKind.Ident, ident);
            }
        }
        return switch (c) {
            case '{' -> new Token(TokenKind.LBrace, "{");
            case '}' -> new Token(TokenKind.RBrace, "}");
            case '(' -> new Token(TokenKind.LParen, "(");
            case ')' -> new Token(TokenKind.RParen, ")");
            case ';' -> new Token(TokenKind.Semicolon, ";");
            case '\0'-> new Token(TokenKind.Eof, "");
            case '"' -> {
                StringBuilder sb = new StringBuilder();
                while(this.peek() != '"')  sb.append(this.advance());
                this.advance();
                String str = sb.toString();
                yield new Token(TokenKind.String, str);
            }
            default  -> new Token(TokenKind.Illegal, String.valueOf(c));
        };
    }
}

class CodeGen {
    Lexer lexer;
    StringBuilder code;
    public CodeGen(Lexer lexer) {
        this.lexer = lexer;
        this.code = new StringBuilder();
        String preamble = """
        .class public Aout
        .super java/lang/Object

        .method public <init>()V
            .limit stack 1
            .limit locals 1
            aload_0
            invokespecial java/lang/Object/<init>()V
            return
        .end method

        """;
        this.code.append(preamble);
    }
    public String getCode() {
        return this.code.toString();
    }
    public void generateCode() {
        Token token;
        while (true) {
            token = this.lexer.nextToken();
            switch(token.kind) {
                case TokenKind.Void: {
                    //NOTE: we expect an ident but i'm too lazy to implement get and expect
                    Token peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.Ident) {
                        System.err.println("Expected identifier after void");
                        System.exit(1);
                    }
                    String ident =  peek_token.lit;
                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.LParen) {
                        System.err.println("Expected ( after " + ident);
                        System.exit(1);
                    }
                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.RParen) {
                        System.err.println("Expected ) after (");
                        System.exit(1);
                    }

                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.LBrace) {
                        System.err.println("Expected { after )");
                        System.exit(1);
                    }
                    if("main".equals(ident)) {
                        code.append(".method public static main([Ljava/lang/String;)V\n");
                    } else {
                        code.append(".method public static "+ ident + "()V\n");
                    }
                    this.code.append("    .limit stack 2\n");
                    this.code.append("    .limit locals 1\n");
                    this.generateCode();
                    this.code.append("    return\n");
                    this.code.append(".end method\n\n");
                    break;
                }
                case TokenKind.Ident: {
                    Token peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.LParen) {
                        System.err.println("Expected ( after " + token.lit);
                        System.exit(1);
                    }
                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.RParen) {
                        System.err.println("Expected ) after (");
                        System.exit(1);
                    }
                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.Semicolon) {
                        System.err.println("Expected ; after )");
                        System.exit(1);
                    }
                    this.code.append("    invokestatic Aout/"+ token.lit + "()V\n");
                    break;
                }
                case TokenKind.Printf: {
                    Token peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.LParen) {
                        System.err.println("Expected ( after printf ");
                        System.exit(1);
                    }
                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.String) {
                        System.err.println("Expected string in printf");
                        System.exit(1);
                    }
                    String str = peek_token.lit;
                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.RParen) {
                        System.err.println("Expected ) after (");
                        System.exit(1);
                    }

                    peek_token = this.lexer.nextToken();
                    if(peek_token.kind != TokenKind.Semicolon) {
                        System.err.println("Expected ; after )");
                        System.exit(1);
                    }
                    this.code.append("    getstatic java/lang/System/out Ljava/io/PrintStream;\n");
                    this.code.append("    ldc \"" + str + "\"\n");
                    this.code.append("    invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
                    break;
                }
                case TokenKind.Eof: case TokenKind.RBrace: return;
                default: {
                    System.err.println("Unhandled case" + token);
                    System.exit(1);
                }
            }
        }
    }
}

public class Compiler {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Please provide file to be compiled");
            System.exit(1);
        }
        Path in_filePath = Paths.get(args[0]);
        Path out_filePath = Paths.get("out.j");
        try {
            String content = Files.readString(in_filePath);
            Lexer lexer = new Lexer(content);
            CodeGen codegen = new CodeGen(lexer);
            codegen.generateCode();
            String code = codegen.getCode();
            Files.write(out_filePath, code.getBytes());
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }
}
