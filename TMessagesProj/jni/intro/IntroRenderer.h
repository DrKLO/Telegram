#ifndef TMESSAGES_INTRO_RENDERER_H
#define TMESSAGES_INTRO_RENDERER_H

#include <GLES2/gl2.h>

extern float scale_factor;
extern int width, height;
extern int y_offset_absolute;

typedef enum {
    Default = 0,
    EaseIn = 1,
    EaseOut = 2,
    EaseInEaseOut = 3,
    Linear = 4,
    Sin = 5,
    EaseOutBounce,
    TIMING_NUM
} timing_type;

typedef float vec2[2];
typedef float vec4[4];
typedef vec4 mat4x4[4];

typedef enum {NORMAL, NORMAL_ONE, RED, BLUE, LIGHT_RED, LIGHT_BLUE} texture_program_type;

typedef struct {
    float x;
    float y;
} CPoint;

typedef struct {
    float width;
    float height;
} CSize;

typedef struct {
    float x;
    float y;
    float z;
} xyz;

typedef struct {
    GLuint program;
    GLuint a_position_location;
    GLuint a_texture_coordinates_location;
    GLint u_mvp_matrix_location;
    GLint u_texture_unit_location;
    GLint u_alpha_loaction;
} TextureProgram;

typedef struct {
    GLuint program;
    GLuint a_position_location;
    GLint u_mvp_matrix_location;
    GLint u_color_location;
    GLint u_alpha_loaction;
} ColorProgram;

typedef struct {
    float side_length;
    float start_angle;
    float end_angle;
    float angle;
    CSize size;
    float radius;
    float width;
} VarParams;

typedef struct {
    GLsizeiptr datasize;
    int round_count;
    GLenum triangle_mode;
    int is_star;
} ConstParams;

typedef struct {
    xyz anchor;
    xyz position;
    float rotation;
    xyz scale;
} LayerParams;

typedef struct {
    xyz anchor;
    xyz position;
    float rotation;
    xyz scale;
    float alpha;
    VarParams var_params;
    ConstParams const_params;
    LayerParams layer_params;
} Params;

typedef struct {
    vec4 color;
    CPoint *data;
    GLuint buffer;
    int num_points;

    Params params;
} Shape;

typedef struct {
    GLuint texture;
    CPoint *data;
    GLuint buffer;
    int num_points;

    Params params;
} TexturedShape;

#endif
