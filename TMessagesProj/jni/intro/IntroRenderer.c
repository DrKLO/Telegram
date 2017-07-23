#include "IntroRenderer.h"
#include <math.h>
#include <stdlib.h>
#include <jni.h>

static int is_initialized = 0;
static float _coefficientsX[TIMING_NUM][4], _coefficientsY[TIMING_NUM][4];
static const float _c0x = 0.0;
static const float _c0y = 0.0;
static const float _c3x = 1.0;
static const float _c3y = 1.0;

float scale_factor;
int width, height;
int y_offset_absolute;
static TextureProgram texture_program;
static TextureProgram texture_program_one;
static TextureProgram texture_program_red;
static TextureProgram texture_program_blue;
static TextureProgram texture_program_light_red;
static TextureProgram texture_program_light_blue;
static TextureProgram *texture_program_temp;
static ColorProgram color_program;
static float y_offset;

#define BUFFER_OFFSET(i) ((void*)(i))

static const vec4 black_color = {0.0f, 0.0f, 0.0f, 1.0f};
static const vec4 white_color = {1.0f, 1.0f, 1.0f, 1.0f};

static LayerParams ribbonLayer, privateLayer;

static TexturedShape spiral;
static Shape mask1;
static Shape cloud_extra_mask1;
static Shape cloud_extra_mask2;
static Shape cloud_extra_mask3;
static Shape cloud_extra_mask4;

static Shape cloud_cover;

static Shape free_bg;
static TexturedShape fast_body;
static TexturedShape fast_arrow_shadow;
static TexturedShape fast_arrow;

static TexturedShape free_knot1;
static TexturedShape free_knot2;
static TexturedShape free_knot3;
static TexturedShape free_knot4;

static Shape powerful_bg;
static TexturedShape powerful_mask, powerful_infinity, powerful_infinity_white;

static Shape private_bg;

static TexturedShape telegram_sphere, telegram_plane;

static Shape cloud_bg;

#define starsCount 80
static TexturedShape star;
static Params stars[starsCount];

static Shape ribbon1;
static Shape ribbon2;
static Shape ribbon3;
static Shape ribbon4;
static mat4x4 stars_matrix;
static mat4x4 main_matrix;
static mat4x4 ribbons_layer;

static TexturedShape ic_bubble_dot, ic_bubble, ic_cam_lens, ic_cam, ic_pencil, ic_pin, ic_smile_eye, ic_smile, ic_videocam;
static GLuint ic_bubble_dot_texture, ic_bubble_texture, ic_cam_lens_texture, ic_cam_texture, ic_pencil_texture, ic_pin_texture, ic_smile_eye_texture, ic_smile_texture, ic_videocam_texture;
static GLuint telegram_sphere_texture, telegram_plane_texture;
static GLuint fast_spiral_texture, fast_body_texture, fast_arrow_texture, fast_arrow_shadow_texture;
static GLuint free_knot_up_texture, free_knot_down_texture;
static GLuint powerful_mask_texture, powerful_star_texture, powerful_infinity_texture, powerful_infinity_white_texture;
static GLuint private_door_texture, private_screw_texture, private_keyhole_body_texture;
static Shape infinity;

static TexturedShape private_door, private_screw, private_keyhole_body;
static Shape private_stroke;

static Shape start_button;

static const float r1 = 58.5f;
static const float r2 = 70;
static double ms0;
static float date, date0;
static float duration_const = 0.3f;
static int direct;
static int i;
static int current_page, prev_page;
static float time;
static mat4x4 ic_matrix;
static LayerParams ic_pin_layer, ic_cam_layer, ic_videocam_layer, ic_smile_layer, ic_bubble_layer, ic_pencil_layer;
static float time_local = 0;
static float knot_delays[4];
static float offset_y;
static float ribbonLength = 86.5f;
static int starsFar = 500;
static float scroll_offset;

static float calculated_speedometer_sin;
float ms0_anim;
int fps_anim;
int count_anim_fps;
static float speedometer_scroll_offset = 0, free_scroll_offset = 0, private_scroll_offset = 0;
float anim_pencil_start_time, anim_pencil_start_all_time, anim_pencil_start_all_end_time;
int anim_pencil_stage;
int anim_bubble_dots_stage;
int anim_bubble_dots_end_period;
float anim_videocam_start_time, anim_videocam_next_time, anim_videocam_duration, anim_videocam_angle, anim_videocam_old_angle;
float anim_cam_start_time, anim_cam_next_time, anim_cam_duration, anim_cam_angle, anim_cam_old_angle;
CPoint anim_cam_position, anim_cam_old_position;
int qShot;
float anim_camshot_start_time, anim_camshot_duration;
float anim_smile_start_time1, anim_smile_start_time2, anim_smile_blink_start_time;
int anim_smile_blink_one;
int anim_smile_stage;
static float scale;
float anim_pin_start_time, anim_pin_duration;
static int anim_pencil_period;
static mat4x4 private_matrix;
float cloud_scroll_offset;

static inline void vec2_add(vec2 r, vec2 a, vec2 b) {
    int i;
    for (i = 0; i < 2; ++i) {
        r[i] = a[i] + b[i];
    }
}

static inline float vec2_mul_inner(vec2 a, vec2 b) {
    float p = 0.f;
    int i;
    for (i = 0; i < 2; ++i) {
        p += b[i]*a[i];
    }
    return p;
}

static inline float vec2_len(vec2 v) {
    return sqrtf(vec2_mul_inner(v, v));
}

static inline void vec2_scale(vec2 r, vec2 v, float s) {
    int i;
    for (i = 0; i < 2; ++i) {
        r[i] = v[i] * s;
    }
}

static inline void vec2_norm(vec2 r, vec2 v) {
    float k = 1.f / vec2_len(v);
    vec2_scale(r, v, k);
}

static inline void mat4x4_identity(mat4x4 M) {
    int i, j;
    for (i = 0; i < 4; ++i) {
        for (j = 0; j < 4; ++j) {
            M[i][j] = i == j ? 1.f : 0.f;
        }
    }
}

static inline void mat4x4_dup(mat4x4 M, mat4x4 N) {
    int i, j;
    for (i = 0; i < 4; ++i) {
        for (j = 0; j < 4; ++j) {
            M[i][j] = N[i][j];
        }
    }
}

static inline void vec4_scale(vec4 r, vec4 v, float s) {
    int i;
    for (i = 0; i < 4; ++i) {
        r[i] = v[i] * s;
    }
}

static inline void mat4x4_scale_aniso(mat4x4 M, mat4x4 a, float x, float y, float z) {
    vec4_scale(M[0], a[0], x);
    vec4_scale(M[1], a[1], y);
    vec4_scale(M[2], a[2], z);
}

static inline void mat4x4_mul(mat4x4 M, mat4x4 a, mat4x4 b) {
    int k, r, c;
    for (c = 0; c < 4; ++c) {
        for (r = 0; r < 4; ++r) {
            M[c][r] = 0.f;
            for (k = 0; k < 4; ++k) {
                M[c][r] += a[k][r] * b[c][k];
            }
        }
    }
}

static inline void mat4x4_mul_vec4(vec4 r, mat4x4 M, vec4 v) {
    int i, j;
    for (j = 0; j < 4; ++j) {
        r[j] = 0.f;
        for (i = 0; i < 4; ++i) {
            r[j] += M[i][j] * v[i];
        }
    }
}

static inline void mat4x4_translate(mat4x4 T, float x, float y, float z) {
    mat4x4_identity(T);
    T[3][0] = x;
    T[3][1] = y;
    T[3][2] = z;
}

static inline void mat4x4_rotate_Z2(mat4x4 Q, mat4x4 M, float angle) {
    float s = sinf(angle);
    float c = cosf(angle);
    mat4x4 R = {
            {c,   s,   0.f, 0.f},
            {-s,  c,   0.f, 0.f},
            {0.f, 0.f, 1.f, 0.f},
            {0.f, 0.f, 0.f, 1.f}
    };
    mat4x4_mul(Q, M, R);
}

static inline void mat4x4_rotate_Z(mat4x4 Q, float angle) {
    mat4x4 temp;
    mat4x4_dup(temp, Q);
    mat4x4_rotate_Z2(Q, temp, angle);
}

static inline void mat4x4_translate_in_place(mat4x4 m, float x, float y, float z)  {
    int i;
    for (i = 0; i < 4; ++i) {
        m[3][i] += m[0][i] * x + m[1][i] * y + m[2][i] * z;
    }
}

static inline float deg_to_radf(float deg) {
    return deg * (float)M_PI / 180.0f;
}

static inline float MAXf(float a, float b) {
    return a > b ? a : b;
}

static inline float MINf(float a, float b) {
    return a < b ? a : b;
}

GLuint compile_shader(const GLenum type, const GLchar* source, const GLint length) {
    GLuint shader_object_id = glCreateShader(type);
    GLint compile_status;
    glShaderSource(shader_object_id, 1, &source, &length);
    glCompileShader(shader_object_id);
    glGetShaderiv(shader_object_id, GL_COMPILE_STATUS, &compile_status);
    return shader_object_id;
}

GLuint link_program(const GLuint vertex_shader, const GLuint fragment_shader) {
    GLuint program_object_id = glCreateProgram();
    GLint link_status;
    glAttachShader(program_object_id, vertex_shader);
    glAttachShader(program_object_id, fragment_shader);
    glLinkProgram(program_object_id);
    glGetProgramiv(program_object_id, GL_LINK_STATUS, &link_status);
    return program_object_id;
}

GLuint build_program(const GLchar * vertex_shader_source, const GLint vertex_shader_source_length, const GLchar * fragment_shader_source, const GLint fragment_shader_source_length) {
    GLuint vertex_shader = compile_shader(GL_VERTEX_SHADER, vertex_shader_source, vertex_shader_source_length);
    GLuint fragment_shader = compile_shader(GL_FRAGMENT_SHADER, fragment_shader_source, fragment_shader_source_length);
    return link_program(vertex_shader, fragment_shader);
}

GLuint create_vbo(const size_t size, const GLvoid* data, const GLenum usage) {
    GLuint vbo_object;
    glGenBuffers(1, &vbo_object);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_object);
    glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr) size, data, usage);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return vbo_object;
}

TextureProgram get_texture_program(GLuint program) {
    return (TextureProgram) {
            program,
            glGetAttribLocation(program, "a_Position"),
            glGetAttribLocation(program, "a_TextureCoordinates"),
            glGetUniformLocation(program, "u_MvpMatrix"),
            glGetUniformLocation(program, "u_TextureUnit"),
            glGetUniformLocation(program, "u_Alpha")};
}

ColorProgram get_color_program(GLuint program) {
    return (ColorProgram) {
            program,
            glGetAttribLocation(program, "a_Position"),
            glGetUniformLocation(program, "u_MvpMatrix"),
            glGetUniformLocation(program, "u_Color"),
            glGetUniformLocation(program, "u_Alpha")};
}

float frand(float from, float to) {
    return (float) (((double) random() / RAND_MAX) * (to - from) + from);
}

int irand(int from, int to) {
    return (int) (((double) random() / RAND_MAX) * (to - from + 1) + from);
}

int signrand() {
    return irand(0, 1) * 2 - 1;
}

static inline float evaluateAtParameterWithCoefficients(float t, float coefficients[]) {
    return coefficients[0] + t*coefficients[1] + t*t*coefficients[2] + t*t*t*coefficients[3];
}

static inline float evaluateDerivationAtParameterWithCoefficients(float t, float coefficients[]) {
    return coefficients[1] + 2*t*coefficients[2] + 3*t*t*coefficients[3];
}

static inline float calcParameterViaNewtonRaphsonUsingXAndCoefficientsForX(float x, float coefficientsX[]) {
    float t = x;
    int i;
    for (i = 0; i < 10; i++) {
        float x2 = evaluateAtParameterWithCoefficients(t, coefficientsX) - x;
        float d = evaluateDerivationAtParameterWithCoefficients(t, coefficientsX);
        float dt = x2 / d;
        t = t - dt;
    }
    return t;
}

static inline float calcParameterUsingXAndCoefficientsForX (float x, float coefficientsX[]) {
    return calcParameterViaNewtonRaphsonUsingXAndCoefficientsForX(x, coefficientsX);
}

float timing(float x, timing_type type) {
    if (is_initialized == 0) {
        is_initialized = 1;

        float c[TIMING_NUM][4];
        c[Default][0] = 0.25f;
        c[Default][1] = 0.1f;
        c[Default][2] = 0.25f;
        c[Default][3] = 1.0f;
        c[EaseInEaseOut][0] = 0.42f;
        c[EaseInEaseOut][1] = 0.0f;
        c[EaseInEaseOut][2] = 0.58f;
        c[EaseInEaseOut][3] = 1.0f;
        c[EaseIn][0] = 0.42f;
        c[EaseIn][1] = 0.0f;
        c[EaseIn][2] = 1.0f;
        c[EaseIn][3] = 1.0f;
        c[EaseOut][0] = 0.0f;
        c[EaseOut][1] = 0.0f;
        c[EaseOut][2] = 0.58f;
        c[EaseOut][3] = 1.0f;
        c[EaseOutBounce][0] = 0.0f;
        c[EaseOutBounce][1] = 0.0f;
        c[EaseOutBounce][2] = 0.0f;
        c[EaseOutBounce][3] = 1.25;
        c[Linear][0] = 0.0;
        c[Linear][1] = 0.0;
        c[Linear][2] = 1.0;
        c[Linear][3] = 1.0;
        int i;
        for (i = 0; i < TIMING_NUM; i++) {
            float _c1x = c[i][0];
            float _c1y = c[i][1];
            float _c2x = c[i][2];
            float _c2y = c[i][3];
            _coefficientsX[i][0] = _c0x;
            _coefficientsX[i][1] = -3.0f * _c0x + 3.0f * _c1x;
            _coefficientsX[i][2] = 3.0f * _c0x - 6.0f * _c1x + 3.0f * _c2x;
            _coefficientsX[i][3] = -_c0x + 3.0f * _c1x - 3.0f * _c2x + _c3x;
            _coefficientsY[i][0] = _c0y;
            _coefficientsY[i][1] = -3.0f * _c0y + 3.0f * _c1y;
            _coefficientsY[i][2] = 3.0f * _c0y - 6.0f * _c1y + 3.0f * _c2y;
            _coefficientsY[i][3] = -_c0y + 3.0f * _c1y - 3.0f * _c2y + _c3y;
        }
    }

    if (x == 0.0 || x == 1.0) {
        return x;
    }
    float t = calcParameterUsingXAndCoefficientsForX(x, _coefficientsX[type]);
    float y = evaluateAtParameterWithCoefficients(t, _coefficientsY[type]);
    return y;
}

void set_y_offset_objects(float a) {
    y_offset = a;
}

void setup_shaders() {
    const char *vshader =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "void main(){"
            "   gl_Position = u_MvpMatrix * a_Position;"
            "}";

    const char *fshader =
            "precision lowp float;"
            "uniform vec4 u_Color;"
            "uniform float u_Alpha;"
            "void main() {"
            "   gl_FragColor = u_Color;"
            "   gl_FragColor.w*=u_Alpha;"
            "}";

    color_program = get_color_program(build_program(vshader, (GLint)strlen(vshader), fshader, (GLint)strlen(fshader)));

    const char *vshader_texture =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "attribute vec2 a_TextureCoordinates;"
            "varying vec2 v_TextureCoordinates;"
            "void main(){"
            "    v_TextureCoordinates = a_TextureCoordinates;"
            "    gl_Position = u_MvpMatrix * a_Position;"
            "}";

    const char *fshader_texture =
            "precision lowp float;"
            "uniform sampler2D u_TextureUnit;"
            "varying vec2 v_TextureCoordinates;"
            "uniform float u_Alpha;"
            "void main(){"
            "    gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);"
            "    gl_FragColor.w *= u_Alpha;"
            "}";

    texture_program = get_texture_program(build_program(vshader_texture, (GLint)strlen(vshader_texture), fshader_texture, (GLint)strlen(fshader_texture)));

    const char *vshader_texture_blue =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "attribute vec2 a_TextureCoordinates;"
            "varying vec2 v_TextureCoordinates;"
            "void main(){"
            "    v_TextureCoordinates = a_TextureCoordinates;"
            "    gl_Position = u_MvpMatrix * a_Position;"
            "}";

    const char *fshader_texture_blue  =
            "precision lowp float;"
            "uniform sampler2D u_TextureUnit;"
            "varying vec2 v_TextureCoordinates;"
            "uniform float u_Alpha;"
            "void main(){"
            "    gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);"
            "   float p = u_Alpha*gl_FragColor.w;"
            "   gl_FragColor = vec4(0,0.6,0.898,p);"
            "}";

    texture_program_blue = get_texture_program(build_program(vshader_texture_blue, (GLint)strlen(vshader_texture_blue), fshader_texture_blue, (GLint)strlen(fshader_texture_blue)));

    const char *vshader_texture_red  =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "attribute vec2 a_TextureCoordinates;"
            "varying vec2 v_TextureCoordinates;"
            "void main(){"
            "    v_TextureCoordinates = a_TextureCoordinates;"
            "    gl_Position = u_MvpMatrix * a_Position;"
            "}";

    const char *fshader_texture_red  =
            "precision lowp float;"
            "uniform sampler2D u_TextureUnit;"
            "varying vec2 v_TextureCoordinates;"
            "uniform float u_Alpha;"
            "void main(){"
            "   gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);"
            "   float p = gl_FragColor.w*u_Alpha;"
            "   gl_FragColor = vec4(210./255.,57./255.,41./255.,p);"
            "}";

    texture_program_red = get_texture_program(build_program(vshader_texture_red, (GLint)strlen(vshader_texture_red), fshader_texture_red, (GLint)strlen(fshader_texture_red)));

    vshader =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "attribute vec2 a_TextureCoordinates;"
            "varying vec2 v_TextureCoordinates;"
            "void main(){"
            "    v_TextureCoordinates = a_TextureCoordinates;"
            "    gl_Position = u_MvpMatrix * a_Position;"
            "}";

    fshader  =
            "precision lowp float;"
            "uniform sampler2D u_TextureUnit;"
            "varying vec2 v_TextureCoordinates;"
            "uniform float u_Alpha;"
            "void main(){"
            "    gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);"
            "    float p = u_Alpha*gl_FragColor.w;"
            "    gl_FragColor = vec4(246./255., 73./255., 55./255., p);"
            "}";

    texture_program_light_red = get_texture_program(build_program(vshader, (GLint)strlen(vshader), fshader, (GLint)strlen(fshader)));

    vshader  =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "attribute vec2 a_TextureCoordinates;"
            "varying vec2 v_TextureCoordinates;"
            "void main(){"
            "    v_TextureCoordinates = a_TextureCoordinates;"
            "    gl_Position = u_MvpMatrix * a_Position;"
            "}";

    fshader  =
            "precision lowp float;"
            "uniform sampler2D u_TextureUnit;"
            "varying vec2 v_TextureCoordinates;"
            "uniform float u_Alpha;"
            "void main(){"
            "    gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);"
            "    float p = u_Alpha*gl_FragColor.w;"
            "    gl_FragColor = vec4(42./255.,180./255.,247./255.,p);"
            "}";

    texture_program_light_blue = get_texture_program(build_program(vshader, (GLint)strlen(vshader), fshader, (GLint)strlen(fshader)));

    vshader  =
            "uniform mat4 u_MvpMatrix;"
            "attribute vec4 a_Position;"
            "attribute vec2 a_TextureCoordinates;"
            "varying vec2 v_TextureCoordinates;"
            "void main(){"
            "    v_TextureCoordinates = a_TextureCoordinates;"
            "    gl_Position = u_MvpMatrix * a_Position;"
            "}";

    fshader  =
            "precision lowp float;"
            "uniform sampler2D u_TextureUnit;"
            "varying vec2 v_TextureCoordinates;"
            "uniform float u_Alpha;"
            "void main(){"
            "    gl_FragColor = texture2D(u_TextureUnit, v_TextureCoordinates);"
            "    gl_FragColor *= u_Alpha;"
            "}";

    texture_program_one = get_texture_program(build_program(vshader, (GLint)strlen(vshader), fshader, (GLint)strlen(fshader)));
}

CPoint CPointMake(float x, float y) {
    CPoint p = {x, y};
    return p;
}

CSize CSizeMake(float width, float height) {
    CSize s = {width, height};
    return s;
}

float D2R(float a) {
    return (float)(a * M_PI / 180.0);
}

float R2D(float a) {
    return (float)(a * 180.0 / M_PI);
}

xyz xyzMake(float x, float y, float z) {
    xyz result;
    result.x = x;
    result.y = y;
    result.z = z;
    return result;
}

LayerParams default_layer_params() {
    LayerParams params;
    params.anchor.x = params.anchor.y = params.anchor.z = 0;
    params.position.x = params.position.y = params.position.z = 0;
    params.rotation = 0;
    params.scale.x = params.scale.y = params.scale.z = 1.0f;
    return params;
}

Params default_params() {
    Params params;
    params.anchor.x = params.anchor.y = params.anchor.z = 0.0f;
    params.position.x = params.position.y = params.position.z = 0.0f;
    params.rotation = 0;
    params.scale.x = params.scale.y = params.scale.z = 1.0f;
    params.alpha = 1.0f;
    params.var_params.side_length = 0;
    params.var_params.start_angle = 0;
    params.var_params.end_angle = 0;
    params.var_params.angle = 0;
    params.var_params.size = CSizeMake(0, 0);
    params.var_params.radius = 0;
    params.var_params.width = 0;
    params.const_params.is_star = 0;
    params.layer_params = default_layer_params();
    return params;
}

void mat4x4_translate_independed(mat4x4 m, float x, float y, float z) {
    mat4x4 tr;
    mat4x4_identity(tr);
    mat4x4_translate_in_place(tr, x, y, z);
    mat4x4 m_dup;
    mat4x4_dup(m_dup, m);
    mat4x4_mul(m, tr, m_dup);
}

static inline void mvp_matrix(mat4x4 model_view_projection_matrix, Params params, mat4x4 view_projection_matrix) {
    mat4x4 model_matrix;
    mat4x4_identity(model_matrix);
    mat4x4 id;
    mat4x4_identity(id);
    mat4x4_translate(model_matrix, -params.anchor.x, -params.anchor.y, params.anchor.z);
    mat4x4 scaled;
    mat4x4_identity(scaled);
    mat4x4_scale_aniso(scaled, scaled, params.scale.x, -params.scale.y, params.scale.z);
    mat4x4 tmp;
    mat4x4_dup(tmp, model_matrix);
    mat4x4_mul(model_matrix, scaled, tmp);
    mat4x4 rotate;
    mat4x4_dup(rotate, id);
    mat4x4_rotate_Z2(rotate, id, deg_to_radf(-params.rotation));
    mat4x4_dup(tmp, model_matrix);
    mat4x4_mul(model_matrix, rotate, tmp);
    mat4x4_translate_independed(model_matrix, params.position.x, -params.position.y, params.position.z);
    mat4x4 model_matrix3;
    mat4x4_identity(model_matrix3);
    mat4x4 mm;
    mat4x4_mul(mm, model_matrix3, view_projection_matrix);
    mat4x4_mul(model_view_projection_matrix, mm, model_matrix);
    mat4x4_translate_independed(model_view_projection_matrix, 0, -y_offset / view_projection_matrix[3][3], 0);
}

void draw_shape(const Shape* shape, mat4x4 view_projection_matrix) {
    if (shape->params.alpha > 0 && (fabs(shape->params.scale.x) > 0 && fabs(shape->params.scale.y) > 0 && fabs(shape->params.scale.z) > 0)) {
        mat4x4 model_view_projection_matrix;
        mvp_matrix(model_view_projection_matrix, shape->params, view_projection_matrix);
        glUseProgram(color_program.program);
        glUniformMatrix4fv(color_program.u_mvp_matrix_location, 1, GL_FALSE, (GLfloat *) model_view_projection_matrix);
        if (shape->params.rotation == 5.0f) {
            glUniform4fv(color_program.u_color_location, 1, shape->color);
        } else if (shape->params.rotation == 10.0f) {
            vec4 col = {0, 1, 0, 1};
            glUniform4fv(color_program.u_color_location, 1, col);
        } else {
            glUniform4fv(color_program.u_color_location, 1, shape->color);
        }
        glUniform1f(color_program.u_alpha_loaction, shape->params.alpha);
        glVertexAttribPointer(color_program.a_position_location, 2, GL_FLOAT, GL_FALSE, sizeof(CPoint), &shape->data[0].x);
        glEnableVertexAttribArray(color_program.a_position_location);
        glDrawArrays(shape->params.const_params.triangle_mode, 0, shape->num_points);
    }
}

void draw_textured_shape(const TexturedShape* shape, mat4x4 view_projection_matrix, texture_program_type program_type) {
    if (shape->params.alpha > 0 && (fabs(shape->params.scale.x) > 0 && fabs(shape->params.scale.y) > 0 && fabs(shape->params.scale.z) > 0)) {
        mat4x4 model_view_projection_matrix;
        mvp_matrix(model_view_projection_matrix, shape->params, view_projection_matrix);
        if (shape->params.const_params.is_star == 1) {
            vec4 pos;
            vec4 vertex = {0, 0, 0, 1};
            mat4x4_mul_vec4(pos, model_view_projection_matrix, vertex);
            vec4 p_NDC = {pos[0] / pos[3], pos[1] / pos[3], pos[2] / pos[3], pos[3] / pos[3]};
            vec4 p_window = {p_NDC[0] * width, -p_NDC[1] * height, 0, 0};
            int d = 160;
            if (fabs(p_window[0]) > d || p_window[1] > y_offset_absolute * 2 + d || p_window[1] < y_offset_absolute * 2 - d) {
                return;
            }
        }
        if (program_type == RED) {
            texture_program_temp = &texture_program_red;
        } else if (program_type == BLUE) {
            texture_program_temp = &texture_program_blue;
        } else if (program_type == LIGHT_RED) {
            texture_program_temp = &texture_program_light_red;
        } else if (program_type == LIGHT_BLUE) {
            texture_program_temp = &texture_program_light_blue;
        } else if (program_type == NORMAL_ONE) {
            texture_program_temp = &texture_program_one;
        } else {
            texture_program_temp = &texture_program;
        }


        glUseProgram(texture_program_temp->program);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, shape->texture);
        glUniformMatrix4fv(texture_program_temp->u_mvp_matrix_location, 1, GL_FALSE, (GLfloat *) model_view_projection_matrix);
        glUniform1i(texture_program_temp->u_texture_unit_location, 0);
        glUniform1f(texture_program_temp->u_alpha_loaction, shape->params.alpha);

        glBindBuffer(GL_ARRAY_BUFFER, shape->buffer);
        glVertexAttribPointer(texture_program_temp->a_position_location, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GL_FLOAT), BUFFER_OFFSET(0));
        glVertexAttribPointer(texture_program_temp->a_texture_coordinates_location, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GL_FLOAT), BUFFER_OFFSET(2 * sizeof(GL_FLOAT)));
        glEnableVertexAttribArray(texture_program_temp->a_position_location);
        glEnableVertexAttribArray(texture_program_temp->a_texture_coordinates_location);
        glDrawArrays(shape->params.const_params.triangle_mode, 0, shape->num_points);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

static inline int size_of_rounded_rectangle_in_vertices(int round_count) {
    return 4*(2+round_count)+2;
}

static inline void gen_rounded_rectangle(CPoint* out, CSize size, float radius, int round_count) {
    int offset = 0;
    out[offset++] = CPointMake(0, 0);
    float k = (float) (M_PI / 2 / (round_count + 1));
    int i = 0;
    int n = 0;
    for (i = (round_count + 2) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * radius, size.height / 2 - radius + sinf(i * k) * radius);
    }
    n++;
    for (i = (round_count + 1) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(-size.width / 2 + radius + cosf(i * k) * radius, size.height / 2 - radius + sinf(i * k) * radius);
    }
    n++;
    for (i = (round_count + 1) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(-size.width / 2 + radius + cosf(i * k) * radius, -size.height / 2 + radius + sinf(i * k) * radius);
    }
    n++;
    for (i = (round_count + 1) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * radius, -size.height / 2 + radius + sinf(i * k) * radius);
    }
    out[offset] = CPointMake(size.width / 2, size.height / 2 - radius);
}

Shape create_rounded_rectangle(CSize size, float radius, int round_count, const vec4 color) {
    int real_vertex_count = size_of_rounded_rectangle_in_vertices(round_count);

    Params params = default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count * 2;
    params.const_params.round_count = round_count;
    params.const_params.triangle_mode = GL_TRIANGLE_FAN;

    params.var_params.size = size;
    params.var_params.radius = radius;
    CPoint *data = malloc(params.const_params.datasize);
    gen_rounded_rectangle(data, params.var_params.size, params.var_params.radius, params.const_params.round_count);
    return (Shape) {{color[0], color[1], color[2], color[3]}, data, create_vbo(params.const_params.datasize, data, GL_DYNAMIC_DRAW), real_vertex_count, params};
}

void change_rounded_rectangle(Shape* shape, CSize size, float radius) {
    if ((*shape).params.var_params.size.width != size.width || (*shape).params.var_params.size.height != size.height || (*shape).params.var_params.radius != radius) {
        (*shape).params.var_params.size.width = size.width;
        (*shape).params.var_params.size.height = size.height;
        (*shape).params.var_params.radius = radius;
        gen_rounded_rectangle((*shape).data, (*shape).params.var_params.size, (*shape).params.var_params.radius, (*shape).params.const_params.round_count);
        glBindBuffer(GL_ARRAY_BUFFER, shape->buffer);
        glBufferSubData(GL_ARRAY_BUFFER, 0, shape->params.const_params.datasize, shape->data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

static inline int size_of_segmented_square_in_vertices() {
    return 7;
}

static inline CPoint square_point(float angle, float radius) {
    CPoint p = {0.0f, 0.0f};
    if (angle <= M_PI / 2 * 0.5f || angle > M_PI / 2 * 3.5f) {
        p = CPointMake(radius, radius * sinf(angle) / cosf(angle));
    } else if (angle <= M_PI / 2 * 1.5) {
        p = CPointMake(radius * cosf(angle) / sinf(angle), radius);
    } else if (angle <= M_PI / 2 * 2.5) {
        p = CPointMake(-radius, -radius * sinf(angle) / cosf(angle));
    } else if (angle <= (float) (M_PI / 2 * 3.5)) {
        p = CPointMake(-radius * cosf(angle) / sinf(angle), -radius);
    }
    return p;
}

static inline CPoint square_texture_point(CPoint p, float side_length) {
    return CPointMake((-p.x / side_length * 0.5f + 0.5f), -p.y / side_length * 0.5f + 0.5f);
}

static inline void gen_segmented_square(CPoint* out, float side_length, float start_angle, float end_angle) {
    CPoint p;
    float radius = side_length;
    int offset = 0;
    float k = 1;
    float da = D2R(-2.6f * 2) * k;
    p = CPointMake(sinf(start_angle + end_angle) * 6 * k, -cosf(start_angle + end_angle) * 6 * k);
    out[offset++] = p;
    out[offset++] = square_texture_point(p, side_length);
    p = square_point(start_angle + da, radius);
    out[offset++] = p;
    out[offset++] = square_texture_point(p, side_length);
    int q = 0;
    int i;
    for (i = (int) start_angle; i < floorf(R2D(start_angle + end_angle + da)); i++) {
        if ((i + 45) % 90 == 0) {
            p = square_point(D2R(i), radius);
            out[offset++] = p;
            out[offset++] = square_texture_point(p, side_length);
            q++;
        }
    }
    p = square_point(start_angle + end_angle + da, radius);
    out[offset++] = p;
    out[offset++] = square_texture_point(p, side_length);
    for (i = 0; i < 4 - q; i++) {
        p = square_point(start_angle + end_angle + da, radius);
        out[offset++] = p;
        out[offset++] = square_texture_point(p, side_length);
    }
}

TexturedShape create_segmented_square(float side_length, float start_angle, float end_angle, GLuint texture) {
    int real_vertex_count = size_of_segmented_square_in_vertices();
    Params params = default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count * 2 * 2;
    params.const_params.triangle_mode = GL_TRIANGLE_FAN;
    CPoint *data = malloc(params.const_params.datasize);
    gen_segmented_square(data, side_length, start_angle, end_angle);
    return (TexturedShape) {texture, data, create_vbo(params.const_params.datasize, data, GL_DYNAMIC_DRAW), real_vertex_count, params};
}

void change_segmented_square(TexturedShape* shape, float side_length, float start_angle, float end_angle) {
    if ((*shape).params.var_params.side_length != side_length || (*shape).params.var_params.start_angle != start_angle || (*shape).params.var_params.end_angle != end_angle) {
        (*shape).params.var_params.side_length = side_length;
        (*shape).params.var_params.start_angle = start_angle;
        (*shape).params.var_params.end_angle = end_angle;
        gen_segmented_square((*shape).data, side_length, start_angle, end_angle);
        glBindBuffer(GL_ARRAY_BUFFER, shape->buffer);
        glBufferSubData(GL_ARRAY_BUFFER, 0, shape->params.const_params.datasize, shape->data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

static inline void gen_rectangle(CPoint* out, CSize size) {
    out[0] = CPointMake(-size.width / 2, -size.height / 2);
    out[1] = CPointMake(size.width / 2, -size.height / 2);
    out[2] = CPointMake(-size.width / 2, size.height / 2);
    out[3] = CPointMake(size.width / 2, size.height / 2);
}

Shape create_rectangle(CSize size, const vec4 color) {
    int real_vertex_count = 4;
    Params params = default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count;
    params.const_params.triangle_mode = GL_TRIANGLE_STRIP;
    CPoint *data = malloc(params.const_params.datasize);
    gen_rectangle(data, size);
    return (Shape) {{color[0], color[1], color[2], color[3]}, data, create_vbo(params.const_params.datasize, data, GL_DYNAMIC_DRAW), real_vertex_count, params};
}

static inline CPoint rectangle_texture_point(CPoint p, CSize size) {
    return CPointMake(1 - (-p.x / size.width + 0.5f), p.y / size.height + 0.5f);
}

static inline void gen_textured_rectangle(CPoint* out, CSize size) {
    out[0] = CPointMake(-size.width / 2, -size.height / 2);
    out[1] = rectangle_texture_point(CPointMake(-size.width / 2, -size.height / 2), size);
    out[2] = CPointMake(size.width / 2, -size.height / 2);
    out[3] = rectangle_texture_point(CPointMake(size.width / 2, -size.height / 2), size);
    out[4] = CPointMake(-size.width / 2, size.height / 2);
    out[5] = rectangle_texture_point(CPointMake(-size.width / 2, size.height / 2), size);
    out[6] = CPointMake(size.width / 2, size.height / 2);
    out[7] = rectangle_texture_point(CPointMake(size.width / 2, size.height / 2), size);
}

TexturedShape create_textured_rectangle(CSize size, GLuint texture) {
    int real_vertex_count = 4;
    Params params = default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count * 2;
    params.const_params.triangle_mode = GL_TRIANGLE_STRIP;
    CPoint *data = malloc(params.const_params.datasize);
    gen_textured_rectangle(data, size);
    return (TexturedShape) {texture, data, create_vbo(params.const_params.datasize, data, GL_STATIC_DRAW), real_vertex_count, params};
}

static inline void gen_ribbon(CPoint* out, float length) {
    out[0] = CPointMake(-MAXf(length - 5.5f, 0), -5.5f);
    out[1] = CPointMake(0, -5.5f);
    out[2] = CPointMake(-MAXf(length, 0), 5.5f);
    out[3] = CPointMake(0, 5.5f);
}

Shape create_ribbon(float length, const vec4 color) {
    int real_vertex_count = 4;
    Params params=default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count;
    params.const_params.triangle_mode = GL_TRIANGLE_STRIP;
    params.var_params.side_length = length;
    CPoint *data = malloc(params.const_params.datasize);
    gen_ribbon(data, length);
    return (Shape) {{color[0], color[1], color[2], color[3]}, data, create_vbo(params.const_params.datasize, data, GL_DYNAMIC_DRAW), real_vertex_count, params};
}

void change_ribbon(Shape* shape, float length) {
    if ((*shape).params.var_params.side_length != length) {
        (*shape).params.var_params.side_length = length;
        gen_ribbon((*shape).data, length);
        glBindBuffer(GL_ARRAY_BUFFER, shape->buffer);
        glBufferSubData(GL_ARRAY_BUFFER, 0, shape->params.const_params.datasize, shape->data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

static inline int size_of_segmented_circle_in_vertices(int num_points) {
    return 1 + (num_points + 1);
}

static inline void gen_circle(CPoint* out, float radius, int vertex_count) {
    int offset = 0;
    out[offset++] = CPointMake(0, 0);
    int i;
    for (i = 0; i <= vertex_count; i++) {
        out[offset++] = CPointMake(radius * (cosf(2 * (float) M_PI * (i / (float) vertex_count))), radius * sinf(2 * (float) M_PI * (i / (float) vertex_count)));
    }
}

Shape create_circle(float radius, int vertex_count, const vec4 color) {
    int real_vertex_count = size_of_segmented_circle_in_vertices(vertex_count);
    Params params = default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count;
    params.const_params.triangle_mode = GL_TRIANGLE_FAN;
    params.const_params.round_count = vertex_count;
    CPoint *data = (CPoint *) malloc(params.const_params.datasize);
    gen_circle(data, radius, vertex_count);
    return (Shape) {{color[0], color[1], color[2], color[3]}, data, create_vbo(params.const_params.datasize, data, GL_STATIC_DRAW), real_vertex_count, params};
}

int size_of_infinity_in_vertices(int segment_count) {
    return (segment_count + 1) * 2;
}

static inline void gen_infinity(CPoint* out, float width, float angle, int segment_count) {
    CPoint path[13];
    path[0] = CPointMake(53, 23);
    path[1] = CPointMake(49, 31);
    path[2] = CPointMake(39, 47);
    path[3] = CPointMake(22, 47);
    path[4] = CPointMake(6, 47);
    path[5] = CPointMake(0, 31);
    path[6] = CPointMake(0, 23);
    path[7] = CPointMake(0, 16);
    path[8] = CPointMake(5, 0);
    path[9] = CPointMake(23, 0);
    path[10] = CPointMake(39, 0);
    path[11] = CPointMake(48, 15);
    path[12] = CPointMake(52, 21);
    int offset = 0;
    int seg;
    for (seg = 0; seg <= segment_count; seg++) {
        float tt = ((float) seg / (float) segment_count) * angle;
        int q = 4;
        float tstep = 1.f / q;
        int n = (int) floor(tt / tstep);
        CPoint a = path[0 + 3 * n];;
        CPoint p1 = path[1 + 3 * n];
        CPoint p2 = path[2 + 3 * n];
        CPoint b = path[3 + 3 * n];
        float t = (tt - tstep * n) * q;
        float nt = 1.0f - t;

        vec2 p = {a.x * nt * nt * nt + 3.0f * p1.x * nt * nt * t + 3.0f * p2.x * nt * t * t + b.x * t * t * t,
                  a.y * nt * nt * nt + 3.0f * p1.y * nt * nt * t + 3.0f * p2.y * nt * t * t + b.y * t * t * t};
        vec2 tangent = {-3.0f * a.x * nt * nt + 3.0f * p1.x * (1.0f - 4.0f * t + 3.0f * t * t) + 3.0f * p2.x * (2.0f * t - 3.0f * t * t) + 3.0f * b.x * t * t,
                        -3.0f * a.y * nt * nt + 3.0f * p1.y * (1.0f - 4.0f * t + 3.0f * t * t) + 3.0f * p2.y * (2.0f * t - 3.0f * t * t) + 3.0f * b.y * t * t};

        vec2 tan_norm = {-tangent[1], tangent[0]};
        vec2 norm;
        vec2_norm(norm, tan_norm);

        vec2 v;
        vec2 norm_scaled;
        vec2_scale(norm_scaled, norm, +width / 2.f);
        vec2_add(v, p, norm_scaled);

        out[offset] = CPointMake(v[0], v[1]);
        offset++;

        vec2_scale(norm_scaled, norm, -width / 2.f);
        vec2_add(v, p, norm_scaled);

        out[offset] = CPointMake(v[0], v[1]);
        offset++;
    }
}

Shape create_infinity(float width, float angle, int segment_count, const vec4 color) {
    int real_vertex_count = size_of_infinity_in_vertices(segment_count);
    Params params = default_params();
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count;
    params.const_params.triangle_mode = GL_TRIANGLE_STRIP;
    params.const_params.round_count = segment_count;
    params.var_params.width = width;
    params.var_params.angle = angle;
    CPoint *data = malloc(params.const_params.datasize);
    gen_infinity(data, width, angle, segment_count);
    return (Shape) {{color[0], color[1], color[2], color[3]}, data, create_vbo(params.const_params.datasize, data, GL_DYNAMIC_DRAW), real_vertex_count, params};
}

void change_infinity(Shape* shape, float angle) {
    if ((*shape).params.var_params.angle != angle) {
        (*shape).params.var_params.angle = angle;
        gen_infinity(shape->data, (*shape).params.var_params.width, (*shape).params.var_params.angle, (*shape).params.const_params.round_count);
        glBindBuffer(GL_ARRAY_BUFFER, shape->buffer);
        glBufferData(GL_ARRAY_BUFFER, shape->params.const_params.datasize, shape->data, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

static inline int size_of_rounded_rectangle_stroked_in_vertices(int round_count) {
    return 4 * (2 + round_count) * 2 + 2;
}

static inline void gen_rounded_rectangle_stroked(CPoint* out, CSize size, float radius, float stroke_width, int round_count) {
    int offset = 0;
    float k = (float) (M_PI / 2 / (round_count + 1));
    float inner_radius = radius - stroke_width;
    int i = 0;
    int n = 0;
    for (i = (round_count + 2) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * radius, size.height / 2 - radius + sinf(i * k) * radius);
        out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * inner_radius, size.height / 2 - radius + sinf(i * k) * inner_radius);
    }
    n++;
    for (i = (round_count + 1) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(-size.width / 2 + radius + cosf(i * k) * radius, size.height / 2 - radius + sinf(i * k) * radius);
        out[offset++] = CPointMake(-size.width / 2 + radius + cosf(i * k) * inner_radius, size.height / 2 - radius + sinf(i * k) * inner_radius);
    }
    n++;
    for (i = (round_count + 1) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(-size.width / 2 + radius + cosf(i * k) * radius, -size.height / 2 + radius + sinf(i * k) * radius);
        out[offset++] = CPointMake(-size.width / 2 + radius + cosf(i * k) * inner_radius, -size.height / 2 + radius + sinf(i * k) * inner_radius);
    }
    n++;
    for (i = (round_count + 1) * n; i <= round_count + 1 + (round_count + 1) * n; i++) {
        out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * radius, -size.height / 2 + radius + sinf(i * k) * radius);
        out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * inner_radius, -size.height / 2 + radius + sinf(i * k) * inner_radius);
    }
    i = 0;
    out[offset++] = CPointMake(size.width / 2 - radius + cosf(i * k) * radius, size.height / 2 - radius + sinf(i * k) * radius);
    out[offset] = CPointMake(size.width / 2 - radius + cosf(i * k) * inner_radius, size.height / 2 - radius + sinf(i * k) * inner_radius);
}

Shape create_rounded_rectangle_stroked(CSize size, float radius, float stroke_width, int round_count, const vec4 color) {
    int real_vertex_count = size_of_rounded_rectangle_stroked_in_vertices(round_count);
    Params params = default_params();
    params.const_params.round_count = round_count;
    params.const_params.datasize = sizeof(CPoint) * real_vertex_count * 2;
    params.var_params.size = size;
    params.var_params.radius = radius;
    params.var_params.width = stroke_width;
    CPoint *data = (CPoint *) malloc(params.const_params.datasize);
    gen_rounded_rectangle_stroked(data, params.var_params.size, params.var_params.radius, params.var_params.width, params.const_params.round_count);
    params.const_params.triangle_mode = GL_TRIANGLE_STRIP;
    return (Shape) {{color[0], color[1], color[2], color[3]}, data, create_vbo(params.const_params.datasize, data, GL_DYNAMIC_DRAW), real_vertex_count, params};
}

void change_rounded_rectangle_stroked(Shape* shape, CSize size, float radius, __unused float stroke_width) {
    if ((*shape).params.var_params.size.width != size.width || (*shape).params.var_params.size.height != size.height || (*shape).params.var_params.radius != radius) {
        (*shape).params.var_params.size.width = size.width;
        (*shape).params.var_params.size.height = size.height;
        (*shape).params.var_params.radius = radius;
        gen_rounded_rectangle_stroked((*shape).data, (*shape).params.var_params.size, (*shape).params.var_params.radius, (*shape).params.var_params.width, (*shape).params.const_params.round_count);
        glBindBuffer(GL_ARRAY_BUFFER, shape->buffer);
        glBufferSubData(GL_ARRAY_BUFFER, 0, shape->params.const_params.datasize, shape->data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}

//------------------------------

float t(float start_value, float end_value, float start_time, float duration, timing_type type) {
    if (time > start_time + duration) {
        return end_value;
    }
    if (type == Linear) {
        return start_value + (end_value - start_value) * MINf(duration + start_time, MAXf(.0, (time - start_time))) / duration;
    }
    return start_value + (end_value - start_value) * timing(MINf(duration + start_time, MAXf(.0, (time - start_time))) / duration, type);
}

float t_reversed(float end_value, float start_value, float start_time, float duration, timing_type type) {
    if (time>start_time+duration) {
        return end_value;
    }
    if (type == Linear) {
        return start_value + (end_value - start_value)*MINf(duration+start_time, MAXf(0.0f, (time - start_time))) /duration;
    }
    return start_value + (end_value - start_value) * timing(MINf(duration+start_time, MAXf(0.0f, (time - start_time))) / duration, type);
}


float t_local(float start_value, float end_value, float start_time, float duration, timing_type type) {
    if (type == Sin) {
        return start_value + (end_value - start_value) * sinf(MINf(MAXf((time_local - start_time) / duration * (float) M_PI, 0), (float) M_PI));
    }

    if (time_local > start_time + duration) {
        return end_value;
    }

    if (type == Linear) {
        return start_value + (end_value - start_value) * MINf(duration + start_time, MAXf(.0, (time_local - start_time))) / duration;
    }
    return start_value + (end_value - start_value) * timing(MINf(duration + start_time, MAXf(.0, (time_local - start_time))) / duration, type);
}

xyz star_create_position(float far) {
    starsFar = 1500;

    int minR = 100;
    int maxR = 1000;

    return xyzMake(signrand() * frand(minR, maxR), signrand() * frand(minR, maxR), far);
}

xyz star_initial_position(int randZ, int forward) {
    starsFar = 1500;

    int minR = 100;
    int maxR = 1000;

    float z = 0;
    if (forward == 1) {
        if (randZ == 0) {
            z = -starsFar;
        } else {
            z = frand(0, -starsFar);
        }
    }

    return xyzMake(signrand() * frand(minR, maxR), signrand() * frand(minR, maxR), z);
}

void draw_stars() {
    float k = (float) width / (float) height;

    set_y_offset_objects(-100 * k * 0);
    for (i = 0; i < starsCount; i++) {
        float stars_scroll_offset = MAXf(0, scroll_offset) * 2;

        float transition_speed;
        if (direct == 1) {
            float s = 5;
            transition_speed = s - t(0, s, 0, duration_const + 1 + 0.8f, Linear);
        } else {
            transition_speed = t(-4, 0, 0, duration_const + 1, EaseOut);
        }

        float speed = stars_scroll_offset + transition_speed;
        stars[i].position.z += speed;

        if (stars[i].position.z > 0 && speed > 0) {
            stars[i].position = star_initial_position(0, 1);
        }
        if (stars[i].position.z < -1500 && speed < 0) {
            stars[i].position = star_initial_position(0, 0);
        }

        float inc = scroll_offset * 100;
        stars[i].position.z = stars[i].position.z + inc;

        star.params.position = stars[i].position;
        float s = 1 + (-stars[i].position.z) / starsFar * 5;

        star.params.scale = xyzMake(s, s, 1);
        float far = starsFar;
        float k = 10.;
        star.params.alpha = (1 - (-stars[i].position.z) / far) * k;
        star.params.alpha = star.params.alpha * star.params.alpha / k;


        draw_textured_shape(&star, stars_matrix, NORMAL);

        stars[i].position.z = stars[i].position.z - inc;
    }

    set_y_offset_objects(offset_y);
}

static inline void mat4x4_plain(mat4x4 M, int width, int height) {
    int i, j;
    for (i = 0; i < 4; ++i) {
        for (j = 0; j < 4; ++j) {
            M[i][j] = 0.0f;
        }
    }

    M[0][0] = 1;
    M[1][1] = 1;
    M[2][2] = 1;
    M[0][0] = 1;
    M[1][1] = (float) width / (float) height;
    M[2][2] = 1;
    M[3][3] = (float) width / 2.0f;
}

static inline void mat4x4_stars(mat4x4 m, float y_fov_in_degrees, float aspect, float n, float f, int width, int height) {
    int is_iOS = 0;
    if (height >= width) {
        float k = (float) width / (float) height;
        float q = !is_iOS ? 1.4f : 0.7f;
        m[0][0] = 1.0f / q;
        m[1][0] = 0.0f;
        m[2][0] = 0.0f;
        m[3][0] = 0.0f;

        m[1][0] = 0.0f;
        m[1][1] = k / q;
        m[1][2] = 0.0f;
        m[1][3] = 0.0f;

        m[2][0] = 0.0f;
        m[2][1] = 0.0f;
        m[2][2] = 1.0f;
        m[2][3] = -1.25f;

        m[3][0] = 0.0f;
        m[3][1] = 0.0f;
        m[3][2] = 0.0f;
        m[3][3] = width * k;
    } else {
        float k = (float) height / (float) width;
        float q = !is_iOS ? 2.0f : 0.7f;

        m[0][0] = 1.0f / q;
        m[1][0] = 0.0f;
        m[2][0] = 0.0f;
        m[3][0] = 0.0f;

        m[1][0] = 0.0f;
        m[1][1] = (1.0f / k) / q;
        m[1][2] = 0.0f;
        m[1][3] = 0.0f;

        m[2][0] = 0.0f;
        m[2][1] = 0.0f;
        m[2][2] = 1.0f;
        m[2][3] = -1.25f;

        m[3][0] = 0.0f;
        m[3][1] = 0.0f;
        m[3][2] = 0.0f;
        m[3][3] = height * k;
    }
    mat4x4_translate_independed(m, 0, -2 * y_offset_absolute / (float) height + 4 * scale_factor / (float) height, 0);
}

void rglNormalDraw() {
    glDisable(GL_DEPTH_TEST);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glColorMask(1,1,1,1);
    glDepthMask(0);
}

void rglMaskDraw() {
    glEnable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDepthMask(1);
    glColorMask(0, 0, 0, 0);
    glDepthFunc(GL_GREATER);
    glClearDepthf(0);
    glClear(GL_DEPTH_BUFFER_BIT);
}

void rglNormalDrawThroughMask() {
    glColorMask(1,1,1,1);
    glDepthFunc(GL_LESS);
    glDepthMask(0);
}

void mat4x4_scaled(mat4x4 matrix, float s) {
    mat4x4_identity(matrix);
    mat4x4_scale_aniso(matrix, matrix, s, s, s);
}

void mat4x4_layer(mat4x4 matrix, LayerParams params, float s, float r) {
    float a = main_matrix[1][1];
    mat4x4 model_matrix;
    mat4x4_identity(model_matrix);
    mat4x4 id;
    mat4x4_identity(id);
    float sc = main_matrix[3][3];
    mat4x4_translate(model_matrix, -params.anchor.x / sc, params.anchor.y / sc * a, params.anchor.z / sc);
    mat4x4 scaled;
    mat4x4_identity(scaled);
    float f = 1.0f;
    mat4x4_scale_aniso(scaled, scaled, params.scale.x * f, params.scale.y * f, params.scale.z * f);
    mat4x4 tmp;
    mat4x4_dup(tmp, model_matrix);
    mat4x4_mul(model_matrix, scaled, tmp);
    mat4x4 rotate;
    mat4x4_dup(rotate, id);
    mat4x4_rotate_Z2(rotate, id, -deg_to_radf(params.rotation));
    mat4x4_dup(tmp, model_matrix);
    mat4x4_mul(model_matrix, rotate, tmp);
    mat4x4_translate_independed(model_matrix, params.position.x / sc, -params.position.y / sc * a, params.position.z / sc);

    mat4x4 m;
    mat4x4_mul(m, model_matrix, main_matrix);

    m[1][0] /= a;
    m[0][1] *= a;

    mat4x4 scale_m;
    mat4x4_scaled(scale_m, s);
    mat4x4_rotate_Z(scale_m, r);
    scale_m[1][0] /= a;
    scale_m[0][1] *= a;
    mat4x4_mul(matrix, scale_m, m);
}

float bubble_dots_sinf(float a) {
    if (a < M_PI * 2 * anim_bubble_dots_end_period) {
        return sinf(a);
    }
    return 0;
}

static void reset_ic() {
    anim_smile_start_time1 = time_local;
    anim_pencil_start_time = 0;
    anim_pencil_start_all_end_time = 0;
    anim_cam_next_time = time_local;
    anim_smile_stage = 0;
    anim_smile_blink_one = 0;
    anim_pencil_stage = 0;
    anim_bubble_dots_end_period = 4;
    anim_pencil_period = 1;
}

static void draw_ic(int type) {
    float rotation;
    float beginTimeK;
    float commonDelay;
    float beginY = 250;
    int bounce;
    texture_program_type COLOR, LIGHT_COLOR;
    if (type == 0) {
        beginTimeK = 2.0f;
        commonDelay = duration_const * 0.5f;
        bounce = 1;
        rotation = -D2R(free_scroll_offset);
        cloud_scroll_offset = 0;
        COLOR = RED, LIGHT_COLOR = LIGHT_RED;
    } else {
        rotation = 0;
        beginTimeK = 2.5;
        commonDelay = 0;
        bounce = 1;
        COLOR = BLUE, LIGHT_COLOR = LIGHT_BLUE;
    }
    float scale;
    float t_y;
    CPoint ic_pos;
    float ic_layer_alpha;
    if (current_page == 1 && direct == 0) {
        ic_layer_alpha = t(1, 0, 0, duration_const * 0.25f, EaseOut);
    } else {
        ic_layer_alpha = 1;
    }
    ic_pin.params.alpha = ic_layer_alpha;
    ic_cam.params.alpha = ic_layer_alpha;
    ic_cam_lens.params.alpha = ic_layer_alpha;
    ic_smile.params.alpha = ic_layer_alpha;
    ic_smile_eye.params.alpha = ic_layer_alpha;
    ic_videocam.params.alpha = ic_layer_alpha;
    ic_bubble.params.alpha = ic_layer_alpha;
    ic_bubble_dot.params.alpha = ic_layer_alpha;
    ic_pencil.params.alpha = ic_layer_alpha;

    if (type == 0) {
        ic_pos = CPointMake(-106 / 2, 61 / 2);
        if (current_page == 1 && direct == 0) {
            t_y = 0;
        } else {
            t_y = t(beginY, 0, commonDelay + duration_const * 0.2f * beginTimeK, duration_const, EaseOut);
            float arg = MAXf(0, time - (commonDelay + duration_const * 0.2f * beginTimeK)) * 50;
            float value = beginY * powf(2.71, -0.055f * arg * 2) * cosf(0.08f * arg) * 0.4f;
            t_y -= value * bounce;
        }
    } else {
        ic_pos = CPointMake(-162 / 2 + 4, +26 / 2 + 20);
        t_y = t(beginY, 0, commonDelay + duration_const * 0.2f * beginTimeK, duration_const, EaseOut);
        float value = 0;
        float e = 2.71;
        float arg = MAXf(0, time - (commonDelay + duration_const * 0.2f * beginTimeK)) * 50;
        value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg - (float) M_PI / 8.0f) * 0.4f;
        t_y -= value * bounce;
    }

    if (time_local > anim_pin_start_time) {
        if (time_local > anim_pin_start_time + anim_pin_duration) {
            anim_pin_start_time = time_local + duration_const * frand(10, 20) * 2;
            anim_pin_duration = duration_const * frand(10, 20) * 2;
        }
    }
    float pinasin = 0;
    ic_pin_layer.position = xyzMake(ic_pos.x + cosf(time_local * 5) * 3 * pinasin + cloud_scroll_offset, ic_pos.y + sinf(time_local * 5) * 1.5f * pinasin + t_y, 0);
    mat4x4_layer(ic_matrix, ic_pin_layer, 1, rotation);
    draw_textured_shape(&ic_pin, ic_matrix, COLOR);

    if (type == 1) {
        ic_videocam_layer.rotation = -30 + t_local(anim_videocam_old_angle, anim_videocam_angle, anim_videocam_start_time, anim_videocam_duration, EaseOut);
        t_y = t(beginY, 0, commonDelay + duration_const * 0.45f * beginTimeK, duration_const, EaseOut);
        float value = 0;
        float e = 2.71;
        float arg = MAXf(0, time - (commonDelay + duration_const * 0.45f * beginTimeK)) * 50;
        value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * 0.4f;
        t_y -= value * bounce;
        if (t_y <= 1 && time_local > anim_videocam_next_time) {
            anim_videocam_duration = duration_const * frand(1.0f, 1.5f) * 1.5f;
            anim_videocam_old_angle = anim_videocam_angle;
            anim_videocam_angle = 15 * irand(-1, 1);
            anim_videocam_start_time = time_local;
            anim_videocam_next_time = time_local + 1000000 + duration_const * frand(5, 8);
        }
        ic_videocam_layer.position = xyzMake(-68 / 2 + cloud_scroll_offset, +80 / 2 + t_y, 0);
        mat4x4_layer(ic_matrix, ic_videocam_layer, 1, rotation);
        draw_textured_shape(&ic_videocam, ic_matrix, COLOR);
    }

    if (type == 0) {
        ic_pos = CPointMake(107 / 2, 78 / 2);
        if (current_page == 1 && direct == 0) {
            t_y = 0;
        } else {
            t_y = t(beginY, 0, commonDelay + duration_const * 0.3f * beginTimeK, duration_const, EaseOut);
            float value = 0;
            float e = 2.71;
            float arg = MAXf(0, time - (commonDelay + duration_const * 0.3f * beginTimeK)) * 50;
            value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * 0.4f;
            t_y -= value * bounce;
        }
    } else {
        ic_pos = CPointMake(-28 / 2, -20 / 2 + 2);
        t_y = t(beginY, 0, commonDelay + duration_const * 0.15f * beginTimeK, duration_const, EaseOut);
        float arg = MAXf(0, time - (commonDelay + duration_const * 0.15f * beginTimeK)) * 50;
        float value = beginY * powf(2.71, -0.055f * arg * 2) * cosf(0.08f * arg) * 0.4f;
        t_y -= value * bounce;
    }

    if (t_y <= 1 && time_local > anim_cam_next_time) {
        anim_cam_duration = duration_const * frand(1.0, 1.5);
        anim_cam_old_angle = anim_cam_angle;
        anim_cam_old_position = anim_cam_position;
        anim_cam_start_time = time_local;
        anim_cam_next_time = time_local + 10000000;
        int r = irand(0, 1);
        if (r == 0) {
            anim_cam_position = CPointMake(-8 + 4, 0);
            anim_cam_angle = signrand() * 10;
        } else if (r == 1) {
            anim_cam_position = CPointMake(4, -5);
            anim_cam_angle = signrand() * 10;
        } else if (r == 2) {
            anim_cam_position = CPointMake(0, 0);
            anim_cam_angle = 0;
        }
        qShot = irand(1, 2);
        anim_camshot_start_time = time_local + duration_const * 0.5f;
        anim_camshot_duration = duration_const * .4f;
    }

    ic_cam_layer.rotation = 15 + t_local(anim_cam_old_angle, anim_cam_angle, anim_cam_start_time, anim_cam_duration, EaseOut);
    ic_cam_layer.position = xyzMake(
            ic_pos.x + 0 * t_local(anim_cam_old_position.x, anim_cam_position.x, anim_cam_start_time, anim_cam_duration, EaseOut) + cloud_scroll_offset,
            ic_pos.y + 0 * t_local(anim_cam_old_position.y, anim_cam_position.y, anim_cam_start_time, anim_cam_duration, EaseOut)
            + t_y,
            0);

    mat4x4_layer(ic_matrix, ic_cam_layer, 1, rotation);
    draw_textured_shape(&ic_cam, ic_matrix, COLOR);

    float lens_scale;
    lens_scale = 1;
    if (qShot >= 0 && time_local > anim_camshot_start_time) {
        lens_scale = t_local(1, 0, anim_camshot_start_time, anim_camshot_duration, Sin);
        if (time_local > anim_camshot_start_time + anim_camshot_duration) {
            qShot--;
            anim_camshot_start_time = time_local + anim_camshot_duration;
        }
    }
    ic_cam_lens.params.scale = xyzMake(lens_scale, lens_scale, 1);
    ic_cam_lens.params.position = xyzMake(0, 1.7, 0);
    draw_textured_shape(&ic_cam_lens, ic_matrix, COLOR);

    if (type == 0) {
        ic_pos = CPointMake(70 / 2, -116 / 2);
        if (current_page == 1 && direct == 0) {
            t_y = 0;
        } else {
            t_y = t(beginY, 0, commonDelay + duration_const * .0f * beginTimeK, duration_const, EaseOut);
            float value = 0;
            float e = 2.71;
            float arg = MAXf(0, time - (commonDelay + duration_const * .0f * beginTimeK)) * 50;
            value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * .4f;
            t_y -= value * bounce;
        }
    } else {
        ic_pos = CPointMake(+60 / 2, 50 / 2);
        t_y = t(beginY, 0, commonDelay + duration_const * 0.25f * beginTimeK, duration_const, EaseOut);

        float value = 0;
        float e = 2.71;
        float arg = MAXf(0, time - (commonDelay + duration_const * 0.25f * beginTimeK)) * 50;
        value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg - (float) M_PI / 8.0f) * .4f;

        t_y -= value * bounce;
    }
    float smile_laught = 0;
    float anim_smile_fade_duration = duration_const * 2;
    float anim_smile_duration = duration_const * 2;
    if (anim_smile_stage == 0) {
        smile_laught = t_local(0, 1, anim_smile_start_time1, anim_smile_fade_duration, Linear);
        if (time_local > anim_smile_duration * 3 + anim_smile_start_time1) {
            anim_smile_stage = 1;
            anim_smile_start_time2 = time_local;
        }
    }

    if (anim_smile_stage == 1) {
        smile_laught = t_local(1, 0, anim_smile_start_time2, anim_smile_fade_duration, Linear);
        if (time_local > anim_smile_duration + anim_smile_start_time2) {
            smile_laught = 0;
            anim_smile_stage = 2;
            anim_smile_blink_one = 1;
            anim_smile_blink_start_time = time_local + duration_const;
        }
    }

    float y = 0;
    if (anim_smile_stage < 2) {
        y = sinf(time_local * (float) M_PI * 10) * 1.5f * smile_laught;
    }

    ic_smile_layer.position = xyzMake(ic_pos.x + cloud_scroll_offset, y + ic_pos.y + t_y, 0);
    mat4x4_layer(ic_matrix, ic_smile_layer, 1, rotation);
    draw_textured_shape(&ic_smile, ic_matrix, COLOR);

    if (time_local > anim_smile_blink_start_time + .1) {

        float blink_pause = frand(3, 6);
        if (irand(0, 3) == 0) {
            blink_pause = .3;
        }
        if (anim_smile_blink_one == 1) {
            blink_pause = frand(3, 6);
        }
        anim_smile_blink_start_time = time_local + blink_pause;

        anim_smile_blink_one = 0;
    }

    int stop_time = 5;
    float eye_scale = t_local(1, 0, anim_smile_blink_start_time, 0.1f, Sin);
    ic_smile_eye.params.scale = xyzMake(1, eye_scale, 1);
    if (time > stop_time) ic_smile_eye.params.scale = xyzMake(1, 1, 1);

    ic_smile_eye.params.position = xyzMake(-7, -4.5f, 0);
    draw_textured_shape(&ic_smile_eye, ic_matrix, COLOR);

    if (anim_smile_blink_one == 1) ic_smile_eye.params.scale = xyzMake(1, 1, 1);
    if (time > stop_time) ic_smile_eye.params.scale = xyzMake(1, 1, 1);
    ic_smile_eye.params.position = xyzMake(7, -4.5f, 0);
    draw_textured_shape(&ic_smile_eye, ic_matrix, COLOR);

    if (type == 0) {
        ic_pos = CPointMake(-60 / 2, 110 / 2);
        if (current_page == 1 && direct == 0) {
            t_y = 0;
        } else {
            t_y = t(beginY, 0, commonDelay + duration_const * .45f * beginTimeK, duration_const, EaseOut);

            float value = 0;
            float e = 2.71;
            float arg = MAXf(0, time - (commonDelay + duration_const * .45f * beginTimeK)) * 50;

            value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * .4f;

            t_y -= value * bounce;
        }
    } else {
        ic_pos = CPointMake(72 / 2, -74 / 2);
        t_y = t(beginY, 0, commonDelay + duration_const * .0f * beginTimeK, duration_const, EaseOut);
        float value = 0;
        float e = 2.71;
        float arg = MAXf(0, time - (commonDelay + duration_const * .0f * beginTimeK)) * 50;
        value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * .4f;
        t_y -= value * bounce;
    }
    ic_bubble_layer.position = xyzMake(ic_pos.x + cloud_scroll_offset, ic_pos.y + t_y, 0);
    mat4x4_layer(ic_matrix, ic_bubble_layer, 1, rotation);
    draw_textured_shape(&ic_bubble, ic_matrix, COLOR);

    scale = 0.7f + 0.2f * bubble_dots_sinf(time * 10);
    ic_bubble_dot.params.scale = xyzMake(scale, scale, scale);
    ic_bubble_dot.params.position = xyzMake(0 - 80.5f, -9 / 2.0f, 0);
    draw_textured_shape(&ic_bubble_dot, ic_matrix, LIGHT_COLOR);

    scale = 0.7f + 0.2f * bubble_dots_sinf((float) -M_PI * 2 / 3 + time * 10);
    if (anim_bubble_dots_stage == 0) scale = MAXf(.7, scale);
    ic_bubble_dot.params.scale = xyzMake(scale, scale, scale);
    ic_bubble_dot.params.position = xyzMake(0, -9 / 2.0f, 0);
    draw_textured_shape(&ic_bubble_dot, ic_matrix, LIGHT_COLOR);

    scale = 0.7f + 0.2f * bubble_dots_sinf((float) -M_PI * 2 / 3 * 2 + time * 10);
    if (anim_bubble_dots_stage == 0) scale = MAXf(.7, scale);
    ic_bubble_dot.params.scale = xyzMake(scale, scale, scale);
    ic_bubble_dot.params.position = xyzMake(0 + 80.5f, -9 / 2.0f, 0);
    draw_textured_shape(&ic_bubble_dot, ic_matrix, LIGHT_COLOR);

    if (type == 0) {
        ic_pos = CPointMake(-88 / 2 - 15, -100 / 2 + 13);
        if (current_page == 1 && direct == 0) {
            t_y = 0;
        } else {
            t_y = t(beginY, 0, commonDelay + duration_const * .1f * beginTimeK, duration_const, EaseOut);
            float value = 0;
            float e = 2.71;
            float arg = MAXf(0, time - (commonDelay + duration_const * .1f * beginTimeK)) * 50;
            value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * .4f;
            t_y -= value * bounce;
        }
    } else {
        ic_pos = CPointMake(+152 / 2 - 17, +66 / 2 + 14);

        t_y = t(beginY, 0, commonDelay + duration_const * 0.35f * beginTimeK, duration_const, EaseOut);

        float value = 0;
        float e = 2.71;
        float arg = MAXf(0, time - (commonDelay + duration_const * 0.35f * beginTimeK)) * 50;
        value = beginY * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg) * 0.4f;

        t_y -= value * bounce;
    }

    float pencil_x = 0;
    if (anim_pencil_stage == 0) {
        ic_pencil_layer.rotation = t_local(0, -5, anim_pencil_start_all_time, duration_const * 0.5f, EaseOut);

        pencil_x = t_local(0, 14, anim_pencil_start_time, 1.5f * 0.85f, Linear);
        if (time_local > anim_pencil_start_time + 1.5 * 0.85) {
            anim_pencil_start_time = time_local;
            anim_pencil_stage = 1;
        }
    } else if (anim_pencil_stage == 1) {
        pencil_x = t_local(14, 0, anim_pencil_start_time, 1.5f * 0.15f, Linear);
        if (time_local > anim_pencil_start_time + 1.5f * 0.15f) {
            if (anim_pencil_period == 0) {
                anim_pencil_start_all_end_time = time_local;
                anim_pencil_start_time = time_local + duration_const * 1;
                anim_pencil_stage = 2;
            } else {
                anim_pencil_period--;
                anim_pencil_start_time = time_local;
                anim_pencil_stage = 0;
            }
        }
    } else if (anim_pencil_stage == 2) {
        ic_pencil_layer.rotation = t_local(-5, 0, anim_pencil_start_all_end_time, duration_const * 0.5f, EaseOut);
        if (time_local > anim_pencil_start_time) {
            anim_pencil_start_all_time = time_local;
            anim_pencil_start_time = time_local;
            anim_pencil_stage = 3;
        }
    }

    float pencil_v = (anim_pencil_stage < 2) ? sinf((float) (time_local * 2 * M_PI * 4)) * 0.8f : 0;
    ic_pencil_layer.position = xyzMake(pencil_x + ic_pos.x + cloud_scroll_offset, pencil_v + ic_pos.y + t_y, 0);
    mat4x4_layer(ic_matrix, ic_pencil_layer, 1, rotation);
    draw_textured_shape(&ic_pencil, ic_matrix, COLOR);
}

void draw_safe(int type, float alpha, float screw_alpha) {
    float screw_distance = 53;

    private_screw.params.alpha = alpha * screw_alpha;

    scale = 1;
    private_screw.params.scale = xyzMake(scale, scale, 1);
    private_screw.params.position = xyzMake(-screw_distance, -screw_distance, 0);
    draw_textured_shape(&private_screw, private_matrix, NORMAL_ONE);

    private_screw.params.scale = xyzMake(scale, scale, 1);
    private_screw.params.position = xyzMake(screw_distance, -screw_distance, 0);
    draw_textured_shape(&private_screw, private_matrix, NORMAL_ONE);

    private_screw.params.scale = xyzMake(scale, scale, 1);
    private_screw.params.position = xyzMake(-screw_distance, screw_distance, 0);
    draw_textured_shape(&private_screw, private_matrix, NORMAL_ONE);

    private_screw.params.scale = xyzMake(scale, scale, 1);
    private_screw.params.position = xyzMake(screw_distance, screw_distance, 0);
    draw_textured_shape(&private_screw, private_matrix, NORMAL_ONE);
}

JNIEXPORT void Java_org_telegram_messenger_Intro_onDrawFrame(JNIEnv *env, jclass class) {
    time_local += 0.016f;

    if (current_page != prev_page) {
        reset_ic();
        ms0_anim = date;
        fps_anim = 0;
        count_anim_fps = 1;
    }

    float knotDelayStep = 0.075f;
    if (prev_page != current_page) {
        for (i = 0; i < 4; i++) {
            knot_delays[i] = (0.65f + knotDelayStep * i) * duration_const;
        }

        for (i = 0; i < 10; i++) {
            int j1 = irand(0, 3);
            int j2 = irand(0, 3);
            float temp = knot_delays[j1];
            knot_delays[j1] = knot_delays[j2];
            knot_delays[j2] = temp;
        }

        if (current_page == 2) {
            ic_pin_layer.rotation = -15;
            ic_cam_layer.rotation = 15;
            ic_smile_layer.rotation = -15;
            ic_bubble_layer.rotation = -15;
        }

        if (current_page == 5) {
            ic_pin_layer.rotation = -15;
            ic_videocam_layer.rotation = -30;
            ic_cam_layer.rotation = 15;
            ic_smile_layer.rotation = -15;
            ic_bubble_layer.rotation = -15;
        }
    }

    fps_anim++;
    if (count_anim_fps == 1 && date - ms0_anim >= duration_const) {
        count_anim_fps = 0;
    }

    if (date - ms0 >= 1.0f) {
        ms0 = date;
    }

    time = date - date0;

    float private_back_k = .8;

    glClearColor(1, 1, 1, 1);
    glClear(GL_COLOR_BUFFER_BIT);


    if (current_page == 0) {
        rglNormalDraw();

        telegram_sphere.params.alpha = 1;

        scale = 1;

        float alpha = 1;
        if (direct == 0) {
            alpha = t(0, 1, 0, duration_const, Linear);

            fast_body.params.alpha = 1;
            fast_body.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&fast_body, main_matrix, NORMAL);
        }

        telegram_sphere.params.alpha = alpha;
        telegram_sphere.params.scale = xyzMake(scale, scale, 1);

        telegram_plane.params.alpha = 1;

        float tt = MINf(0, (float) (-M_PI * 125. / 180. + time * M_PI * 2 * 1.5));

        float dx = sinf(tt) * 75;
        float dy = -sinf(tt) * 60;

        telegram_plane.params.position = xyzMake(dx, dy, 0);

        float scale = (cosf(tt) + 1) * 0.5f;

        telegram_plane.params.scale = xyzMake(cosf(tt) * scale, scale, 1);

        if (tt < D2R(125)) {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }
    } else if (current_page == 1) {
        rglNormalDraw();
        if (direct == 1) {
            fast_body.params.scale = xyzMake(1, 1, 1);
            fast_body.params.alpha = 1;
            draw_textured_shape(&fast_body, main_matrix, NORMAL);
        } else {
            fast_body.params.alpha = t(0, 1, .0, duration_const, Linear);;
            float scale = t(.95, 1, 0, duration_const, EaseInEaseOut);
            fast_body.params.scale = xyzMake(scale, scale, 1.0f);
            draw_textured_shape(&fast_body, main_matrix, NORMAL);
        }
    } else if (current_page == 2) {
        rglNormalDraw();
        if (direct == 1) {
            fast_body.params.alpha = t(1.0f, .0, .0, duration_const, Linear);;
            float scale = t(1, .95, 0, duration_const, EaseInEaseOut);
            fast_body.params.scale = xyzMake(scale, scale, 1.0f);
            draw_textured_shape(&fast_body, main_matrix, NORMAL);
        }
    } else if (current_page == 4) {
        if (direct == 1) {
            privateLayer.rotation = private_scroll_offset + t(-90, 0, 0, duration_const, EaseOut);
        } else {
            privateLayer.rotation = private_scroll_offset + t(90, 0, 0, duration_const * private_back_k, EaseOut);
        }
        mat4x4_layer(private_matrix, privateLayer, 1.0f, 0);
    }

    rglMaskDraw();
    mask1.params.position.z = cloud_extra_mask1.params.position.z = cloud_extra_mask2.params.position.z = cloud_extra_mask3.params.position.z = cloud_extra_mask4.params.position.z = 1;

    if (current_page == 0) {
        if (direct == 0) {
            change_rounded_rectangle(&mask1, CSizeMake(r1 * 2, r1 * 2), r1);
            mask1.params.rotation = 0;
        }
    } else if (current_page == 1) {
        if (direct == 1) {
            change_rounded_rectangle(&mask1, CSizeMake(r1 * 2, r1 * 2), r1);
            mask1.params.rotation = 0;
        } else {
            float size = t(r2 * 2, r1 * 2, 0, duration_const, EaseInEaseOut);
            float round = t(30, r1, 0, duration_const, EaseInEaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(size, size), round);
            free_scroll_offset = 0;
            mask1.params.rotation = t(180, 0.0f, 0, duration_const, EaseInEaseOut) + free_scroll_offset;
        }
    } else if (current_page == 2) {
        if (direct == 1) {
            float size = t(r1 * 2, r2 * 2, 0, duration_const, EaseInEaseOut);
            float round = t(r1, 30, 0, duration_const, EaseInEaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(size, size), round);
            free_scroll_offset = scroll_offset * 5;
            mask1.params.rotation = t(0, 180.0f, 0, duration_const, EaseInEaseOut) + free_scroll_offset;
        } else {
            free_scroll_offset = scroll_offset * 5;
            float r = 316 / 4.0f;
            float size = t_reversed(r2 * 2, r * 2, 0, duration_const, EaseInEaseOut);
            float round = t_reversed(30, 20, 0, duration_const, EaseInEaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(size, size), round);
            mask1.params.rotation = t_reversed(180.0f + free_scroll_offset, 180.0f + 90.0f, 0, duration_const, EaseInEaseOut);
        }
    } else if (current_page == 3) {
        if (direct == 1) {
            float r = 316 / 4.0f;
            float size = t(r2 * 2, r * 2, 0, duration_const, EaseInEaseOut);
            float round = t(30, 20, 0, duration_const, EaseInEaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(size, size), round);
            mask1.params.rotation = t(180.0f + free_scroll_offset, 180.0f + 90.0f, 0, duration_const, EaseInEaseOut);
        } else {
            float r = 316 / 4.0f;
            float size = t_reversed(r * 2, r2 * 2, 0, duration_const, EaseOut);
            float round = t_reversed(20, 30, 0, duration_const, EaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(size, size), round);
            mask1.params.rotation = t_reversed(180.0f + 90.0f, 180.0f + 90.0f + 90.0f, 0, duration_const, EaseOut);
            mask1.params.position = xyzMake(0, 0, mask1.params.position.z);
        }
    } else if (current_page == 4) {
        if (direct == 1) {
            float r = 316 / 4.0f;
            float size = t(r * 2, r2 * 2, 0, duration_const, EaseOut);
            float round = t(20, 30, 0, duration_const, EaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(size, size), round);
            mask1.params.rotation = private_scroll_offset + t(180.0f + 90.0f, 180.0f + 90.0f + 90.0f, 0, duration_const, EaseOut);
            mask1.params.position = xyzMake(0, 0, mask1.params.position.z);
        } else {
            float k = 0;
            k = 1.0f * private_back_k;
            float scale = t_reversed(r2 * 2, 100, 0, duration_const * k, EaseOut);
            change_rounded_rectangle(&mask1, CSizeMake(scale, scale), t_reversed(30, 50, 0, duration_const * k, EaseOut));
            mask1.params.position = xyzMake(t_reversed(0, 29 / 2, 0, duration_const * k, EaseOut), t_reversed(0, -19 / 2, 0, duration_const * k, EaseOut), mask1.params.position.z);
            mask1.params.rotation = private_scroll_offset + t_reversed(180.0f + 90.0f + 90.0f, 180.0f + 90.0f + 90.0f + 90.0f, 0, duration_const * k, EaseOut);

            k = 1.0f * private_back_k;
            int sublayer2_radius = 33;
            cloud_extra_mask1.params.position = xyzMake(t_reversed(0, -122 / 2, 0, duration_const * k, EaseOut), t_reversed(0, 54 / 2 - 1, 0, duration_const * k, EaseOut), cloud_extra_mask1.params.position.z);
            scale = t_reversed(0, sublayer2_radius, 0, duration_const * k, EaseOut);
            cloud_extra_mask1.params.scale = xyzMake(scale, scale, 1);
            draw_shape(&cloud_extra_mask1, main_matrix);

            k = 1.15f * private_back_k;
            int sublayer3_radius = 94 / 4;
            cloud_extra_mask2.params.position = xyzMake(t_reversed(0, -84 / 2, 0, duration_const * k, EaseOut), t_reversed(0, -29 / 2, 0, duration_const * k, EaseOut), cloud_extra_mask2.params.position.z);
            scale = t_reversed(0, sublayer3_radius, 0, duration_const * k, EaseOut);
            cloud_extra_mask2.params.scale = xyzMake(scale, scale, 1);
            draw_shape(&cloud_extra_mask2, main_matrix);

            k = 1.3f * private_back_k;
            int sublayer4_radius = 124 / 4;
            cloud_extra_mask3.params.position = xyzMake(t_reversed(0, 128 / 2, 0, duration_const * k, EaseOut), t_reversed(0, 56 / 2, 0, duration_const * k, EaseOut), cloud_extra_mask3.params.position.z);
            scale = t_reversed(0, sublayer4_radius, 0, duration_const * k, EaseOut);
            cloud_extra_mask3.params.scale = xyzMake(scale, scale, 1);
            draw_shape(&cloud_extra_mask3, main_matrix);

            k = 1.5f * private_back_k;
            int sublayer5_radius = 64;
            cloud_extra_mask4.params.position = xyzMake(t_reversed(0, 0, 0, duration_const * k, EaseOut), t_reversed(0, 50, 0, duration_const * k, EaseOut), cloud_extra_mask4.params.position.z);
            scale = t_reversed(0, sublayer5_radius, 0, duration_const * k, EaseOut);
            cloud_extra_mask4.params.scale = xyzMake(scale, scale, 1);
            draw_shape(&cloud_extra_mask4, main_matrix);
        }
    } else if (current_page == 5) {
        float k = 0.8f;
        float scale = t(r2 * 2, 100, 0, duration_const * k, EaseOut);
        change_rounded_rectangle(&mask1, CSizeMake(scale, scale), t(30, 50, 0, duration_const * k, EaseOut));
        mask1.params.position = xyzMake(t(0, 29 / 2, 0, duration_const * k, EaseOut), t(0, -19 / 2, 0, duration_const * k, EaseOut), mask1.params.position.z);
        mask1.params.rotation = t(180.0f + 90.0f + 90.0f, 180.0f + 90.0f + 90.0f + 90.0f, 0, duration_const * k, EaseOut);

        k = 1.0f;
        int sublayer2_radius = 33;
        cloud_extra_mask1.params.position = xyzMake(t(0, -122 / 2, 0, duration_const * k, EaseOut), t(0, 54 / 2 - 1, 0, duration_const * k, EaseOut), cloud_extra_mask1.params.position.z);
        scale = t(0, sublayer2_radius, 0, duration_const * k, EaseOut);
        cloud_extra_mask1.params.scale = xyzMake(scale, scale, 1);
        draw_shape(&cloud_extra_mask1, main_matrix);


        k = 1.15;
        int sublayer3_radius = 94 / 4;
        cloud_extra_mask2.params.position = xyzMake(t(0, -84 / 2, 0, duration_const * k, EaseOut), t(0, -29 / 2, 0, duration_const * k, EaseOut), cloud_extra_mask2.params.position.z);
        scale = t(0, sublayer3_radius, 0, duration_const * k, EaseOut);
        cloud_extra_mask2.params.scale = xyzMake(scale, scale, 1);
        draw_shape(&cloud_extra_mask2, main_matrix);


        k = 1.3;
        int sublayer4_radius = 124 / 4;
        cloud_extra_mask3.params.position = xyzMake(t(0, 128 / 2, 0, duration_const * k, EaseOut), t(0, 56 / 2, 0, duration_const * k, EaseOut), cloud_extra_mask3.params.position.z);
        scale = t(0, sublayer4_radius, 0, duration_const * k, EaseOut);
        cloud_extra_mask3.params.scale = xyzMake(scale, scale, 1);
        draw_shape(&cloud_extra_mask3, main_matrix);


        k = 1.5f;
        int sublayer5_radius = 64;
        cloud_extra_mask4.params.position = xyzMake(t(0, 0, 0, duration_const * k, EaseOut), t(0, 50, 0, duration_const * k, EaseOut), cloud_extra_mask4.params.position.z);
        scale = t(0, sublayer5_radius, 0, duration_const * k, EaseOut);
        cloud_extra_mask4.params.scale = xyzMake(scale, scale, 1);
        draw_shape(&cloud_extra_mask4, main_matrix);

    }
    draw_shape(&mask1, main_matrix);

    int rr = 30;
    int seg = 15;
    int ang = 180;
    rglNormalDrawThroughMask();
    if (current_page == 0) {
        if (direct == 0) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            change_segmented_square(&spiral, r1, D2R(rr + seg), D2R(speedometer_scroll_offset + calculated_speedometer_sin + t(-seg + ang, 0, 0, duration_const, EaseOut)));

            spiral.params.scale = xyzMake(1, 1, 1);
            spiral.params.rotation = t(180.0f, 0, 0, duration_const, EaseOut);
            spiral.params.alpha = t(1, 0, 0, duration_const, Linear);
            draw_textured_shape(&spiral, main_matrix, NORMAL_ONE);

            fast_arrow.params.alpha = fast_arrow_shadow.params.alpha = t(1, 0, 0, duration_const, Linear);
            fast_arrow.params.rotation = fast_arrow_shadow.params.rotation = t(rr, rr - 180 - 160, 0, duration_const, EaseOut) + speedometer_scroll_offset + calculated_speedometer_sin;
            draw_textured_shape(&fast_arrow_shadow, main_matrix, NORMAL_ONE);
            draw_textured_shape(&fast_arrow, main_matrix, NORMAL_ONE);
        }
    } else if (current_page == 1) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        speedometer_scroll_offset = scroll_offset * 25;
        calculated_speedometer_sin = 0;

        if (direct == 1) {

            float value = 0;
            float e = 2.71;

            float arg = time * 50;

            value = 180 - 180 * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg * 3);

            float ta = t(0, 180.0f, 0, duration_const, EaseOut);

            change_segmented_square(&spiral, r1, D2R(rr + seg), D2R(-seg + value + speedometer_scroll_offset));

            spiral.params.scale = xyzMake(1, 1, 1);
            spiral.params.rotation = ta;
            spiral.params.alpha = t(0, 1, 0, duration_const, Linear);
            draw_textured_shape(&spiral, main_matrix, NORMAL_ONE);

            fast_arrow.params.alpha = fast_arrow_shadow.params.alpha = t(0, 1, 0, duration_const, Linear);
            fast_arrow.params.rotation = fast_arrow_shadow.params.rotation = -330 + value + ta + speedometer_scroll_offset;
            draw_textured_shape(&fast_arrow_shadow, main_matrix, NORMAL_ONE);
            draw_textured_shape(&fast_arrow, main_matrix, NORMAL_ONE);

        } else {
            spiral.params.alpha = fast_arrow.params.alpha = fast_arrow_shadow.params.alpha = 1;

            float value = 0;
            float e = 2.71;

            float arg = time * 50;

            float dangle = 90;
            value = 180 - 90 - (180 - 90) * powf(e, -0.055f * arg * 2) * cosf(0.08f * arg * 3);
            value *= -1;

            change_segmented_square(&spiral, r1, D2R(rr + seg), D2R(speedometer_scroll_offset + value + calculated_speedometer_sin + t(360, 360 - dangle - seg, 0, duration_const, EaseInEaseOut)));

            float scale = t(1.18, 1, 0, duration_const, EaseInEaseOut);
            spiral.params.scale = xyzMake(scale, scale, 1);
            spiral.params.rotation = t(360, 180, 0, duration_const, EaseInEaseOut);
            draw_textured_shape(&spiral, main_matrix, NORMAL);

            fast_arrow.params.rotation = fast_arrow_shadow.params.rotation = speedometer_scroll_offset + value + calculated_speedometer_sin + t(rr + 360 + 6, rr + 360 - 180 - dangle, 0, duration_const, EaseInEaseOut);
            draw_textured_shape(&fast_arrow_shadow, main_matrix, NORMAL);
            draw_textured_shape(&fast_arrow, main_matrix, NORMAL);

            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            free_bg.params.alpha = t(1, 0, 0, duration_const, Linear);
            draw_shape(&free_bg, main_matrix);

            draw_ic(0);
        }
    } else if (current_page == 2) {

        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        if (direct == 1) {
            spiral.params.alpha = fast_arrow.params.alpha = fast_arrow_shadow.params.alpha = 1;

            change_segmented_square(&spiral, r1, D2R(rr + seg + speedometer_scroll_offset), D2R(t(-seg + ang, 360, 0, duration_const, EaseInEaseOut)));

            float scale = t(1, 1.18, 0, duration_const, EaseInEaseOut);
            spiral.params.scale = xyzMake(scale, scale, 1);
            spiral.params.rotation = t(180, 360, 0, duration_const, EaseInEaseOut);
            draw_textured_shape(&spiral, main_matrix, NORMAL);

            fast_arrow.params.rotation = fast_arrow_shadow.params.rotation = speedometer_scroll_offset + t(rr, rr + 360 + 6, 0, duration_const, EaseInEaseOut);
            draw_textured_shape(&fast_arrow_shadow, main_matrix, NORMAL);
            draw_textured_shape(&fast_arrow, main_matrix, NORMAL);

            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            free_bg.params.alpha = t(0, 1, 0, duration_const, Linear);
            draw_shape(&free_bg, main_matrix);

            draw_ic(0);
        } else {
            glDisable(GL_BLEND);
            free_bg.params.alpha = 1;
            draw_shape(&free_bg, main_matrix);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            draw_ic(0);

            powerful_bg.params.alpha = t_reversed(0, 1, 0, duration_const, Linear);
            draw_shape(&powerful_bg, main_matrix);
        }
        ribbon1.params.rotation = 0;
        ribbon2.params.rotation = 90;
        ribbon3.params.rotation = 180;
        ribbon4.params.rotation = 270;
    } else if (current_page == 3) {
        if (direct == 1) {
            glDisable(GL_BLEND);
            free_bg.params.alpha = 1;
            draw_shape(&free_bg, main_matrix);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            powerful_bg.params.alpha = t(0, 1, 0, duration_const, Linear);
            draw_shape(&powerful_bg, main_matrix);

            draw_stars();

        } else {
            glDisable(GL_BLEND);
            private_bg.params.alpha = 1;
            draw_shape(&private_bg, main_matrix);

            float a = t(0, 1.0f, 0, duration_const, EaseOut);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            powerful_bg.params.rotation = 0;
            powerful_bg.params.alpha = a;
            draw_shape(&powerful_bg, main_matrix);

            draw_stars();
        }
    } else if (current_page == 4) {
        if (direct == 1) {

            glDisable(GL_BLEND);
            powerful_bg.params.alpha = 1;
            draw_shape(&powerful_bg, main_matrix);

            float a = t(0, 1.0f, 0, duration_const, EaseOut);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            private_bg.params.rotation = t(45, 0, 0, duration_const, EaseOut);
            private_bg.params.alpha = a;
            draw_shape(&private_bg, main_matrix);

        } else {
            glDisable(GL_BLEND);
            cloud_bg.params.alpha = 1;
            draw_shape(&cloud_bg, main_matrix);

            float a = t(0, 1.0f, 0, duration_const * private_back_k, EaseOut);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            private_bg.params.alpha = a;
            draw_shape(&private_bg, main_matrix);
        }
    } else if (current_page == 5) {
        glDisable(GL_BLEND);
        private_bg.params.alpha = 1.0f;
        draw_shape(&private_bg, main_matrix);
        float a = t(0, 1.0f, 0, duration_const, EaseOut);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        cloud_bg.params.alpha = a;
        draw_shape(&cloud_bg, main_matrix);
        if (scroll_offset > 0) {
            cloud_scroll_offset = -scroll_offset * 40;
        } else {
            cloud_scroll_offset = -scroll_offset * 15;
        }
        draw_ic(1);
    }

    if (current_page == 0) {
        rglNormalDraw();
        if (direct == 0) {
            telegram_sphere.params.alpha = t(0, 1, 0, duration_const * 0.8f, Linear);
            scale = 1;

            telegram_sphere.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&telegram_sphere, main_matrix, NORMAL);

            float tt = MINf(0, (float) (-M_PI * 125.0f / 180.0f + time * M_PI * 2 * 1.5f));
            float dx = sinf(tt) * 75;
            float dy = -sinf(tt) * 60;
            telegram_plane.params.position = xyzMake(dx, dy, 0);
            float scale = (cosf(tt) + 1) * 0.5f;
            telegram_plane.params.scale = xyzMake(cosf(tt) * scale, scale, 1);

            if (tt < D2R(125)) {
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                draw_textured_shape(&telegram_plane, main_matrix, NORMAL_ONE);
            }
        }
    } else if (current_page == 1) {
        rglNormalDraw();
        if (direct == 1) {
            telegram_sphere.params.alpha = t(1, 0, 0, duration_const, Linear);
            draw_textured_shape(&telegram_sphere, main_matrix, NORMAL);

            double tt = time * M_PI * 2 * 1.5f;

            float dx = (float) sin(tt) * 75;
            float dy = (float) -sin(tt) * 60;

            telegram_plane.params.position = xyzMake(dx, dy, 0);

            float scale = (float) (cos(tt) + 1) * 0.5f;

            telegram_plane.params.scale = xyzMake((float) cos(tt) * scale, scale, 1);

            if (tt < D2R(125)) {
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                draw_textured_shape(&telegram_plane, main_matrix, NORMAL_ONE);
            }
        }
    } else if (current_page == 2) {
        rglNormalDraw();

        float dribbon = 87;

        if (direct == 1) {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            ribbonLayer.rotation = scroll_offset * 5 + t(180, 360, 0, duration_const, EaseInEaseOut);
            mat4x4_layer(ribbons_layer, ribbonLayer, 1.0f, 0);

            float scale;
            float dur = duration_const * 0.5f;

            free_knot1.params.position = xyzMake(5, -5 - 9, 0);
            scale = t(0, 1, knot_delays[0], dur, EaseOut);
            free_knot1.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&free_knot1, ribbons_layer, NORMAL_ONE);

            free_knot2.params.position = xyzMake(-5, -5 - 9, 0);
            scale = t(0, 1, knot_delays[1], dur, EaseOut);
            free_knot2.params.scale = xyzMake(-scale, scale, 1);
            draw_textured_shape(&free_knot2, ribbons_layer, NORMAL_ONE);

            free_knot3.params.position = xyzMake(-5, 5 - 9, 0);
            scale = t(0, 1, knot_delays[2], dur, EaseOut);
            free_knot3.params.scale = xyzMake(-scale, scale, 1);
            draw_textured_shape(&free_knot3, ribbons_layer, NORMAL_ONE);

            free_knot3.params.position = xyzMake(5, 5 - 9, 0);
            scale = t(0, 1, knot_delays[3], dur, EaseOut);
            free_knot3.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&free_knot3, ribbons_layer, NORMAL_ONE);


            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            ribbon1.params.alpha = ribbon2.params.alpha = ribbon3.params.alpha = ribbon4.params.alpha = t(0, 1, 0, dur, EaseInEaseOut);

            int ribbon_k = time > duration_const ? 1 : 0;

            change_ribbon(&ribbon1, ribbonLength - 8.0f * ribbon_k - free_scroll_offset / 5.0f * (30 - 8 * ribbon_k));
            ribbon1.params.position.x = scroll_offset * 30 * 0 + t(-dribbon, 0, 0, duration_const, EaseInEaseOut);
            draw_shape(&ribbon1, ribbons_layer);

            change_ribbon(&ribbon2, ribbonLength - 10.0f * ribbon_k - free_scroll_offset / 5.0f * (22 - 10 * ribbon_k));
            ribbon2.params.position.y = scroll_offset * 15 + t(-9 - dribbon, -9, 0, duration_const, EaseInEaseOut);
            draw_shape(&ribbon2, ribbons_layer);

            ribbon3.params.position.x = t(dribbon, 0, 0, duration_const, EaseInEaseOut);;
            draw_shape(&ribbon3, ribbons_layer);

            ribbon4.params.position.y = t(-9 + dribbon, -9, 0, duration_const, EaseInEaseOut);;
            draw_shape(&ribbon4, ribbons_layer);

            ribbonLayer.anchor.y = 0;
            ribbonLayer.position.y = 0;

            change_ribbon(&ribbon1, ribbonLength);
            change_ribbon(&ribbon2, ribbonLength);
            change_ribbon(&ribbon3, ribbonLength);
            change_ribbon(&ribbon4, ribbonLength);
        } else {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            float scale = t(1, 2, 0, duration_const, EaseIn);
            powerful_mask.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&powerful_mask, main_matrix, NORMAL_ONE);


            ribbonLayer.rotation = free_scroll_offset + t_reversed(360, 360 + (45 + 30), 0, duration_const, EaseOut);
            ribbonLayer.position.y = t_reversed(0, -8, 0, duration_const * 0.8f, EaseOut);
            ribbonLayer.anchor.y = t_reversed(0, -9, 0, duration_const * 0.8f, EaseOut);
            mat4x4_layer(ribbons_layer, ribbonLayer, 1.0f, 0);


            float dur = duration_const * 0.5f;

            free_knot1.params.position = xyzMake(11 / 2, -11 / 2 - 9, 0);
            scale = t(0, 1, knot_delays[0], dur, EaseOut);
            free_knot1.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&free_knot1, ribbons_layer, NORMAL_ONE);

            free_knot2.params.position = xyzMake(-11 / 2, -11 / 2 - 9, 0);
            scale = t(0, 1, knot_delays[1], dur, EaseOut);
            free_knot2.params.scale = xyzMake(-scale, scale, 1);
            draw_textured_shape(&free_knot2, ribbons_layer, NORMAL_ONE);

            free_knot3.params.position = xyzMake(-11 / 2, 11 / 2 - 9, 0);
            scale = t(0, 1, knot_delays[2], dur, EaseOut);
            free_knot3.params.scale = xyzMake(-scale, scale, 1);
            draw_textured_shape(&free_knot3, ribbons_layer, NORMAL_ONE);

            free_knot3.params.position = xyzMake(11 / 2, 11 / 2 - 9, 0);
            scale = t(0, 1, knot_delays[3], dur, EaseOut);
            free_knot3.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&free_knot3, ribbons_layer, NORMAL_ONE);

            float a1 = -25;
            ribbon1.params.rotation = t_reversed(0, a1, 0, duration_const, EaseOut);
            ribbon3.params.rotation = t_reversed(180, 180 + a1, 0, duration_const, EaseOut);

            float a2 = 0;
            ribbon2.params.rotation = t_reversed(90, 90 + a2, 0, duration_const, EaseOut);
            ribbon4.params.rotation = t_reversed(270, 270 + a2, 0, duration_const, EaseOut);

            float k = .9;
            ribbon2.params.alpha = ribbon4.params.alpha = t_reversed(1, 0, duration_const * 0.5f, duration_const * 0.1f, Linear);

            int ribbon_k = 0;
            change_ribbon(&ribbon1, t_reversed(ribbonLength - 8.0f * ribbon_k, 0, 0, duration_const * 0.9f, Linear) - free_scroll_offset / 5.0f * (30 - 8 * ribbon_k));
            ribbon1.params.position.x = 0;
            draw_shape(&ribbon1, ribbons_layer);

            change_ribbon(&ribbon2, t_reversed(ribbonLength - 10.0f * ribbon_k, 0, 0, duration_const * k, Linear) - free_scroll_offset / 5.0f * (22 - 10 * ribbon_k));
            ribbon2.params.position.y = scroll_offset * 15 + -9;
            draw_shape(&ribbon2, ribbons_layer);

            change_ribbon(&ribbon3, t_reversed(ribbonLength, 0, 0, duration_const * 0.9f, Linear));
            draw_shape(&ribbon3, ribbons_layer);

            change_ribbon(&ribbon4, t_reversed(ribbonLength, 0, duration_const * 0.6f * 0, duration_const * k, Linear));
            draw_shape(&ribbon4, ribbons_layer);

            float infinityDurK = 1.3;

            rglMaskDraw();

            change_infinity(&infinity, t_reversed(0, 0.99, 0, duration_const * infinityDurK, EaseOut));

            float rot1 = t(0, -50, duration_const * 0.5f, duration_const * 0.8f, EaseOut);
            float rot2 = t(0, -30, duration_const * 0.8f, duration_const, EaseOut);
            infinity.params.rotation = rot1;

            infinity.params.position.z = 1;
            infinity.params.position.y = -6;
            infinity.params.anchor = xyzMake(52.75, 23.5f, 0);

            float infinity_scale = 1.025;
            infinity.params.scale = xyzMake(infinity_scale, infinity_scale, 1);
            draw_shape(&infinity, main_matrix);
            infinity.params.scale = xyzMake(-infinity_scale, -infinity_scale, 1);
            draw_shape(&infinity, main_matrix);
            rglNormalDrawThroughMask();
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            powerful_infinity_white.params.rotation = rot1 + rot2;
            powerful_infinity_white.params.alpha = 1;
            powerful_infinity_white.params.position.y = -6;
            draw_textured_shape(&powerful_infinity_white, main_matrix, NORMAL_ONE);
        }
    } else if (current_page == 3) {
        if (direct == 1) {
            ribbon1.params.position.x = 0;
            ribbon2.params.position.y = -9;
            ribbon3.params.position.x = 0;
            ribbon4.params.position.y = -9;
            rglNormalDraw();
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            float scale = t(2, 1, 0, duration_const, EaseOut);
            powerful_mask.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&powerful_mask, main_matrix, NORMAL_ONE);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            ribbonLayer.rotation = free_scroll_offset + t(360, 360 + (45 + 30), 0, duration_const * 0.8f, EaseOut);
            ribbonLayer.position.y = t(0, -8, 0, duration_const * 0.8f, EaseOut);
            ribbonLayer.anchor.y = t(0, -9, 0, duration_const * 0.8f, EaseOut);
            mat4x4_layer(ribbons_layer, ribbonLayer, 1.0f, 0);

            float a1 = -25;
            ribbon1.params.rotation = t(0, a1, 0, duration_const, EaseOut);
            ribbon3.params.rotation = t(180, 180 + a1, 0, duration_const, EaseOut);

            float a2 = 0;
            ribbon2.params.rotation = t(90, 90 + a2, 0, duration_const, EaseOut);
            ribbon4.params.rotation = t(270, 270 + a2, 0, duration_const, EaseOut);

            float k = .5;
            ribbon2.params.alpha = ribbon4.params.alpha = t(1, 0, duration_const * k * 0.5f, duration_const * k * 0.1f, Linear);

            int ribbon_k = time > duration_const ? 1 : 0;

            change_ribbon(&ribbon1, t(ribbonLength - 8.0f * ribbon_k - free_scroll_offset / 5.0f * (30 - 8 * ribbon_k), 0, 0, duration_const * 0.9f, Linear));
            draw_shape(&ribbon1, ribbons_layer);

            change_ribbon(&ribbon2, t(ribbonLength - 10.0f * ribbon_k - free_scroll_offset / 5.0f * (22 - 10 * ribbon_k), 0, 0, duration_const * k, Linear));
            draw_shape(&ribbon2, ribbons_layer);

            change_ribbon(&ribbon3, t(ribbonLength, 0, 0, duration_const * 0.9f, Linear));
            draw_shape(&ribbon3, ribbons_layer);

            change_ribbon(&ribbon4, t(ribbonLength, 0, 0, duration_const * k, Linear));
            draw_shape(&ribbon4, ribbons_layer);

            float infinityDurK = 1.1f;
            if (time < duration_const * infinityDurK - 0.025f) {
                rglMaskDraw();

                change_infinity(&infinity, t(0, 0.99f, 0, duration_const * infinityDurK, Linear));

                infinity.params.rotation = 0;

                infinity.params.position.z = 1;
                infinity.params.position.y = -6;
                infinity.params.anchor = xyzMake(52.75, 23.5f, 0);

                float infinity_scale = 1.025f;
                infinity.params.scale = xyzMake(infinity_scale, infinity_scale, 1);
                draw_shape(&infinity, main_matrix);

                infinity.params.scale = xyzMake(-infinity_scale, -infinity_scale, 1);
                draw_shape(&infinity, main_matrix);

                rglNormalDrawThroughMask();
                glEnable(GL_BLEND);
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

                powerful_infinity_white.params.rotation = 0;
                powerful_infinity_white.params.alpha = 1;
                powerful_infinity_white.params.position.y = -6;

                draw_textured_shape(&powerful_infinity_white, main_matrix, NORMAL_ONE);
            } else {
                rglNormalDraw();

                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                powerful_infinity.params.position.y = -6;
                powerful_infinity.params.alpha = 1;
                draw_textured_shape(&powerful_infinity, main_matrix, NORMAL_ONE);
            }
        } else {
            rglNormalDraw();
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            float scale = t(2, 1, 0, duration_const, EaseOut);
            powerful_mask.params.scale = xyzMake(scale, scale, 1);
            draw_textured_shape(&powerful_mask, main_matrix, NORMAL_ONE);

            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            scale = t(1, 2, 0, duration_const, EaseOut);
            private_stroke.params.scale = xyzMake(scale, scale, 1);
            private_stroke.params.rotation = t(0, -90, 0, duration_const, EaseOut);
            private_stroke.params.alpha = t(1, 0, 0, duration_const, Linear);
            private_stroke.params.position = xyzMake(0, t(0, -6, 0, duration_const, EaseOut), 0);
            scale = t_reversed(63 * 2.0f, 63 * 2, 0, duration_const, EaseOut);
            change_rounded_rectangle_stroked(&private_stroke, CSizeMake(scale, scale), scale / 2.0f, 9);
            draw_shape(&private_stroke, main_matrix);

            float infinityDurK = 1.1;
            if (time < duration_const * infinityDurK - 0.025f) {
                rglMaskDraw();

                change_infinity(&infinity, t(0, 0.99, 0, duration_const * infinityDurK, Linear));

                infinity.params.rotation = 0;

                infinity.params.position.z = 1;
                infinity.params.position.y = -6;
                infinity.params.anchor = xyzMake(52.75, 23.5f, 0);

                float infinity_scale = 1.025;
                infinity.params.scale = xyzMake(infinity_scale, infinity_scale, 1);
                draw_shape(&infinity, main_matrix);

                infinity.params.scale = xyzMake(-infinity_scale, -infinity_scale, 1);
                draw_shape(&infinity, main_matrix);


                rglNormalDrawThroughMask();
                glEnable(GL_BLEND);
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

                powerful_infinity_white.params.rotation = 0;
                powerful_infinity_white.params.alpha = 1;
                powerful_infinity_white.params.position.y = -6;

                draw_textured_shape(&powerful_infinity_white, main_matrix, NORMAL_ONE);
            } else {
                rglNormalDraw();

                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                powerful_infinity.params.position.y = -6;
                powerful_infinity.params.alpha = 1;
                draw_textured_shape(&powerful_infinity, main_matrix, NORMAL_ONE);
            }
        }
    } else if (current_page == 4) {
        private_stroke.params.scale = xyzMake(1, 1, 1);

        private_scroll_offset = scroll_offset * 5;

        rglNormalDraw();
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        scale = t(1, 2, 0, duration_const, EaseOut);
        if (scale < 1.5) {
            powerful_mask.params.scale = xyzMake(scale, scale, 1);
        }

        if (direct == 1) {
            privateLayer.rotation = private_scroll_offset + t(-90, 0, 0, duration_const, EaseOut);
        } else {
            privateLayer.rotation = private_scroll_offset + t(90, 0, 0, duration_const * private_back_k, EaseOut);
        }

        mat4x4_layer(private_matrix, privateLayer, 1, 0);

        if (direct == 1) {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            powerful_infinity.params.position.y = -6;
            powerful_infinity.params.alpha = t(1, 0, 0, duration_const * 0.25f, EaseIn);
            draw_textured_shape(&powerful_infinity, main_matrix, NORMAL_ONE);
        }

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (direct == 1) {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            scale = t(0.5f, 1.0f, 0, duration_const, EaseOut);
            private_door.params.scale = xyzMake(scale, scale, 1);
            private_door.params.alpha = t(.0, 1.0f, 0, duration_const, EaseOut);
            draw_textured_shape(&private_door, main_matrix, NORMAL_ONE);

            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            private_stroke.params.rotation = private_scroll_offset;
            private_stroke.params.alpha = 1;
            private_stroke.params.position = xyzMake(0, 0, 0);
            scale = t(63, 63 * 2, 0, duration_const, EaseOut);
            change_rounded_rectangle_stroked(&private_stroke, CSizeMake(scale, scale), scale / 2.0f, 9);
            draw_shape(&private_stroke, main_matrix);

            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            float k = .0;
            scale = t(0.5f, 1.0f, duration_const * k, duration_const, EaseOut);
            private_keyhole_body.params.rotation = private_scroll_offset;
            private_keyhole_body.params.scale = xyzMake(scale, scale, 1);
            private_keyhole_body.params.alpha = t(.0, 1.0f, duration_const * k, duration_const, EaseOut);
            draw_safe(0, 1, t(0, 1, 0, duration_const, Linear));
        } else {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            scale = t(0.5f, 1.0f, 0, duration_const * private_back_k, EaseOut);
            private_door.params.scale = xyzMake(scale, scale, 1);
            private_door.params.alpha = t(.0, 1.0f, 0, duration_const * private_back_k, EaseOut);
            draw_textured_shape(&private_door, main_matrix, NORMAL_ONE);

            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            private_stroke.params.rotation = private_scroll_offset;
            private_stroke.params.alpha = t(0, 1, 0, duration_const * 0.25f, Linear);
            private_stroke.params.position = xyzMake(0, 0, 0);
            scale = t(63, 63 * 2, 0, duration_const * private_back_k, EaseOut);
            change_rounded_rectangle_stroked(&private_stroke, CSizeMake(scale, scale), scale / 2.0f, 9);
            draw_shape(&private_stroke, main_matrix);

            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            scale = t(00.5f, 1.0, 0, duration_const * private_back_k, EaseOut);
            private_keyhole_body.params.rotation = private_scroll_offset;
            private_keyhole_body.params.scale = xyzMake(scale, scale, 1);
            private_keyhole_body.params.alpha = t(.0, 1.0f, 0, duration_const * private_back_k, EaseOut);

            if (time < duration_const * .4) {
                cloud_cover.params.position.y = t_reversed(118 / 2 + 50, 118 / 2, duration_const * 0.8f * private_back_k, duration_const * private_back_k, EaseOut);
                draw_shape(&cloud_cover, main_matrix);
            }

            draw_safe(0, t(0, 1, duration_const * private_back_k * 0.0f, duration_const * private_back_k, Linear), t(0, 1, 0, duration_const, Linear));
        }
    } else if (current_page == 5) {
        float private_fade_k = 0.5f;

        rglNormalDraw();

        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        scale = 1;
        private_door.params.scale = xyzMake(scale, scale, 1);
        private_door.params.alpha = t(1, 0, 0, duration_const * private_fade_k * 0.5f, EaseOut);
        draw_textured_shape(&private_door, main_matrix, NORMAL_ONE);

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        private_stroke.params.rotation = private_scroll_offset;
        private_stroke.params.alpha = t(1, 0, 0, duration_const * private_fade_k * 0.5f, EaseOut);
        scale = t(244 / 2, r2 * 2, 0, duration_const, EaseOut);
        change_rounded_rectangle_stroked(&private_stroke, CSizeMake(scale, scale), scale / 2.0f, 9);
        draw_shape(&private_stroke, main_matrix);

        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        scale = 1;
        private_keyhole_body.params.rotation = private_scroll_offset;
        private_keyhole_body.params.scale = xyzMake(scale, scale, 1);
        private_keyhole_body.params.alpha = t(1.0, 0.0, 0, duration_const * private_fade_k * 0.5f, EaseOut);
        privateLayer.rotation = private_scroll_offset;
        mat4x4_layer(private_matrix, privateLayer, t(1, 0.9f, 0, duration_const * private_fade_k, EaseOut), 0);
        cloud_cover.params.position.y = t(118 / 2 + 50, 118 / 2, 0, duration_const, EaseOut);
        draw_shape(&cloud_cover, main_matrix);
    }

    prev_page = current_page;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setScrollOffset(JNIEnv *env, jclass class, float a_offset) {
    scroll_offset = a_offset;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setPage(JNIEnv *env, jclass class, int page) {
    if (current_page == page) {
        return;
    } else {
        prev_page = current_page;
        current_page = page;
        direct = current_page > prev_page ? 1 : 0;
        date0 = date;
        time = 0;
    }
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setDate(JNIEnv *env, jclass class, float a) {
    date = a;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setIcTextures(JNIEnv *env, jclass class, GLuint a_ic_bubble_dot, GLuint a_ic_bubble, GLuint a_ic_cam_lens, GLuint a_ic_cam, GLuint a_ic_pencil, GLuint a_ic_pin, GLuint a_ic_smile_eye, GLuint a_ic_smile, GLuint a_ic_videocam) {
    ic_bubble_dot_texture = a_ic_bubble_dot;
    ic_bubble_texture = a_ic_bubble;
    ic_cam_lens_texture = a_ic_cam_lens;
    ic_cam_texture = a_ic_cam;
    ic_pencil_texture = a_ic_pencil;
    ic_pin_texture = a_ic_pin;
    ic_smile_eye_texture = a_ic_smile_eye;
    ic_smile_texture = a_ic_smile;
    ic_videocam_texture = a_ic_videocam;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setTelegramTextures(JNIEnv *env, jclass class, GLuint a_telegram_sphere, GLuint a_telegram_plane) {
    telegram_sphere_texture = a_telegram_sphere;
    telegram_plane_texture = a_telegram_plane;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setFastTextures(JNIEnv *env, jclass class, GLuint a_fast_body, GLuint a_fast_spiral, GLuint a_fast_arrow, GLuint a_fast_arrow_shadow) {
    fast_spiral_texture = a_fast_spiral;
    fast_body_texture = a_fast_body;
    fast_arrow_shadow_texture = a_fast_arrow_shadow;
    fast_arrow_texture = a_fast_arrow;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setFreeTextures(JNIEnv *env, jclass class, GLuint a_knot_up, GLuint a_knot_down) {
    free_knot_up_texture = a_knot_up;
    free_knot_down_texture = a_knot_down;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setPowerfulTextures(JNIEnv *env, jclass class, GLuint a_powerful_mask, GLuint a_powerful_star, GLuint a_powerful_infinity, GLuint a_powerful_infinity_white) {
    powerful_mask_texture = a_powerful_mask;
    powerful_star_texture = a_powerful_star;
    powerful_infinity_texture = a_powerful_infinity;
    powerful_infinity_white_texture = a_powerful_infinity_white;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_setPrivateTextures(JNIEnv *env, jclass class, GLuint a_private_door, GLuint a_private_screw) {
    private_door_texture = a_private_door;
    private_screw_texture = a_private_screw;
}

JNIEXPORT void Java_org_telegram_messenger_Intro_onSurfaceCreated(JNIEnv *env, jclass class) {

    ms0 = 0;
    date = 1;
    date0 = 0;
    direct = 0;
    i = 0;
    current_page = 0;
    prev_page = 0;
    time = 0;
    time_local = 0;
    offset_y = 0;
    ribbonLength = 86.5f;
    starsFar = 500;
    scroll_offset = 0;
    calculated_speedometer_sin = 0;
    ms0_anim = 0;
    fps_anim = 0;
    count_anim_fps = 0;
    speedometer_scroll_offset = 0;
    free_scroll_offset = 0;
    private_scroll_offset = 0;
    anim_pencil_start_time = 0;
    anim_pencil_start_all_time = 0;
    anim_pencil_start_all_end_time = 0;
    anim_pencil_stage = 0;
    anim_bubble_dots_stage = 0;
    anim_bubble_dots_end_period = 0;
    anim_videocam_start_time = 0;
    anim_videocam_next_time = 0;
    anim_videocam_duration = 0;
    anim_videocam_angle = 0;
    anim_videocam_old_angle = 0;
    anim_cam_start_time = 0;
    anim_cam_next_time = 0;
    anim_cam_duration = 0;
    anim_cam_angle = 0;
    anim_cam_old_angle = 0;
    qShot = 0;
    anim_camshot_start_time = 0;
    anim_camshot_duration = 0;
    anim_smile_start_time1 = 0;
    anim_smile_start_time2 = 0;
    anim_smile_blink_start_time = 0;
    anim_smile_blink_one = 0;
    anim_smile_stage = 0;
    scale = 0;
    anim_pin_start_time = 0;
    anim_pin_duration = 0;
    anim_pencil_period = 0;
    cloud_scroll_offset = 0;

    setup_shaders();

    vec4 start_button_col = {44 / 255.0f, 165 / 255.0f, 224 / 255.0f, 1.0f};
    start_button = create_rounded_rectangle(CSizeMake(172, 44), 2, 3, start_button_col);
    start_button.params.anchor.y = -22;

    mask1 = create_rounded_rectangle(CSizeMake(60, 60), 0, 16, black_color);

    telegram_sphere = create_textured_rectangle(CSizeMake(148, 148), telegram_sphere_texture);
    telegram_plane = create_textured_rectangle(CSizeMake(82, 74), telegram_plane_texture);
    telegram_plane.params.anchor = xyzMake(6, -5, 0);

    fast_body = create_textured_rectangle(CSizeMake(148, 148), fast_body_texture);

    fast_arrow_shadow = create_textured_rectangle(CSizeMake(164 / 2, 44 / 2), fast_arrow_shadow_texture);
    fast_arrow_shadow.params.position.x = -1;
    fast_arrow_shadow.params.position.y = 2;

    fast_arrow = create_textured_rectangle(CSizeMake(164 / 2, 44 / 2), fast_arrow_texture);
    fast_arrow.params.anchor.x = fast_arrow_shadow.params.anchor.x = -19;

    int ang = 180;
    spiral = create_segmented_square(r1, D2R(35 + 1), D2R(35 + 1 - 10 + ang), fast_spiral_texture);

    vec4 free_bg_color = {246 / 255.0f, 73 / 255.0f, 55 / 255.0f, 1};
    free_bg = create_rectangle(CSizeMake(160 * 2, 160 * 2), free_bg_color);

    free_knot1 = create_textured_rectangle(CSizeMake(138 / 3, 138 / 3), free_knot_up_texture);
    free_knot1.params.anchor.x = -23 + 10;
    free_knot1.params.anchor.y = 23 - 10;

    free_knot2 = create_textured_rectangle(CSizeMake(138 / 3, 138 / 3), free_knot_up_texture);
    free_knot2.params.anchor.x = -23 + 10;
    free_knot2.params.anchor.y = 23 - 10;

    free_knot3 = create_textured_rectangle(CSizeMake(150 / 3, 150 / 3), free_knot_down_texture);
    free_knot3.params.anchor.x = -100 / 4.0f + 20 / 2.0f;
    free_knot3.params.anchor.y = -100 / 4.0f + 20 / 2.0f;

    free_knot4 = create_textured_rectangle(CSizeMake(150 / 3, 150 / 3), free_knot_down_texture);
    free_knot4.params.anchor.x = -100 / 4.0f + 20 / 2.0f;
    free_knot4.params.anchor.y = -100 / 4.0f + 20 / 2.0f;


    ribbonLayer = default_layer_params();

    ribbon1 = create_ribbon(ribbonLength, white_color);
    ribbon1.params.layer_params = ribbonLayer;

    ribbon2 = create_ribbon(ribbonLength, white_color);
    ribbon2.params.rotation = 90;
    ribbon2.params.layer_params = ribbonLayer;

    ribbon3 = create_ribbon(ribbonLength, white_color);
    ribbon3.params.rotation = 180;
    ribbon3.params.layer_params = ribbonLayer;

    ribbon4 = create_ribbon(ribbonLength, white_color);
    ribbon4.params.rotation = 270;
    ribbon4.params.layer_params = ribbonLayer;

    ribbon1.params.position.y = ribbon2.params.position.y = ribbon3.params.position.y = ribbon4.params.position.y = -9;


    ic_bubble_dot = create_textured_rectangle(CSizeMake(18 / 3, 18 / 3), ic_bubble_dot_texture);
    ic_bubble = create_textured_rectangle(CSizeMake(102 / 3, 102 / 3), ic_bubble_texture);
    ic_cam_lens = create_textured_rectangle(CSizeMake(36 / 3, 36 / 3), ic_cam_lens_texture);
    ic_cam = create_textured_rectangle(CSizeMake(108 / 3, 96 / 3), ic_cam_texture);
    ic_pencil = create_textured_rectangle(CSizeMake(86 / 3, 86 / 3), ic_pencil_texture);
    ic_pin = create_textured_rectangle(CSizeMake(90 / 3, 120 / 3), ic_pin_texture);
    ic_smile_eye = create_textured_rectangle(CSizeMake(18 / 3, 18 / 3), ic_smile_eye_texture);
    ic_smile = create_textured_rectangle(CSizeMake(120 / 3, 120 / 3), ic_smile_texture);
    ic_videocam = create_textured_rectangle(CSizeMake(144 / 3, 84 / 3), ic_videocam_texture);

    ic_pin_layer = ic_cam_layer = ic_videocam_layer = ic_smile_layer = ic_bubble_layer = ic_pencil_layer = default_layer_params();

    ic_pin_layer.anchor = xyzMake(0, 50 / 2, 0);
    ic_pencil_layer.anchor = xyzMake(-30 / 2, 30 / 2, 0);

    infinity = create_infinity(11.7, .0, 32, white_color);

    vec4 powerful_bg_color = {47 / 255.f, 90 / 255.f, 131 / 255.f, 1};
    powerful_bg = create_rectangle(CSizeMake(200, 200), powerful_bg_color);
    powerful_mask = create_textured_rectangle(CSizeMake(200, 200), powerful_mask_texture);

    powerful_infinity = create_textured_rectangle(CSizeMake(366 / 3, 180 / 3), powerful_infinity_texture);
    powerful_infinity_white = create_textured_rectangle(CSizeMake(366 / 3, 180 / 3), powerful_infinity_white_texture);

    float star_radius = 5.25;
    star = create_textured_rectangle(CSizeMake(star_radius, star_radius), powerful_star_texture);
    star.params.const_params.is_star = 1;
    for (i = 0; i < starsCount; i++) {
        stars[i] = default_params();
        stars[i].position = star_create_position(-(i * 1500.0f) / starsCount);
    }

    privateLayer = default_layer_params();

    vec4 private_bg_color = {200 / 255.f, 207 / 255.f, 212 / 255.f, 1};
    private_bg = create_rectangle(CSizeMake(240, 240), private_bg_color);

    private_door = create_textured_rectangle(CSizeMake(408 / 3, 408 / 3), private_door_texture);
    private_keyhole_body = create_textured_rectangle(CSizeMake(216 / 3, 216 / 3), private_keyhole_body_texture);
    private_screw = create_textured_rectangle(CSizeMake(30 / 3, 30 / 3), private_screw_texture);
    private_stroke = create_rounded_rectangle_stroked(CSizeMake(244 / 2, 244 / 2), 21, 9, 16, white_color);

    int cloud_polygons_count = 64;
    cloud_extra_mask1 = create_circle(1, cloud_polygons_count, black_color);
    cloud_extra_mask2 = create_circle(1, cloud_polygons_count, black_color);
    cloud_extra_mask3 = create_circle(1, cloud_polygons_count, black_color);
    cloud_extra_mask4 = create_circle(1, cloud_polygons_count, black_color);

    cloud_cover = create_rectangle(CSizeMake(240, 100), white_color);
    cloud_cover.params.anchor.y = -50;

    vec4 cloud_color = {42 / 255.0f, 180 / 255.0f, 247 / 255.0f, 1};
    cloud_bg = create_rectangle(CSizeMake(160 * 2, 160 * 2), cloud_color);
}

JNIEXPORT void Java_org_telegram_messenger_Intro_onSurfaceChanged(JNIEnv *env, jclass class, int a_width_px, int a_height_px, float a_scale_factor, int a1) {
    glViewport(0, 0, a_width_px, a_height_px);
    width = (int) (a_width_px / a_scale_factor);
    height = (int) (a_height_px / a_scale_factor);
    scale_factor = a_scale_factor;
    mat4x4_plain(main_matrix, (int)((float)a_width_px / a_scale_factor), (int)((float)a_height_px / a_scale_factor));
    offset_y = a1*main_matrix[1][1];
    set_y_offset_objects(offset_y);
    y_offset_absolute = a1;
    mat4x4_stars(stars_matrix, 45, 1, -1000, 0, (int)((float)a_width_px / a_scale_factor), (int)((float)a_height_px / a_scale_factor));
}