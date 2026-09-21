package com.exame.ordem.questoes.service;

import com.exame.ordem.questoes.dto.*;
import com.exame.ordem.questoes.internal.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestoesQueryServiceImplTest {

    @Mock ProvaRepository provaRepository;
    @Mock QuestaoRepository questaoRepository;
    @Mock AlternativaRepository alternativaRepository;
    @Mock DisciplinaRepository disciplinaRepository;

    @InjectMocks QuestoesQueryServiceImpl service;

    // ================================================================
    //  buscarQuestoesPorProva
    // ================================================================

    @Test
    @DisplayName("buscarQuestoesPorProva → retorna questões da prova encontrada")
    void buscarQuestoesPorProva_encontrada() {
        Prova prova = prova(5, "43º EXAME DE ORDEM UNIFICADO - PROVA TIPO 1 (BRANCA)", 2025);
        Questao q1 = questao(100, prova, 1, false, disciplina(1, "Ética"));
        Questao q2 = questao(101, prova, 2, false, disciplina(1, "Ética"));
        q1.getAlternativas().add(alternativa(1001, q1, "A", true));
        q2.getAlternativas().add(alternativa(1002, q2, "B", true));

        when(provaRepository.findAll()).thenReturn(List.of(prova));
        when(questaoRepository.findByProvaIdComAlternativas(5))
                .thenReturn(List.of(q1, q2));

        List<QuestaoResumo> resumo = service.buscarQuestoesPorProva(43);

        assertThat(resumo).hasSize(2);
        assertThat(resumo.get(0).numero()).isEqualTo(1);
        assertThat(resumo.get(1).numero()).isEqualTo(2);
        assertThat(resumo.get(0).provaId()).isEqualTo(5);
    }

    @Test
    @DisplayName("buscarQuestoesPorProva → IllegalArgumentException quando não encontra")
    void buscarQuestoesPorProva_naoEncontrada() {
        when(provaRepository.findAll()).thenReturn(List.of(
                prova(5, "43º EXAME DE ORDEM UNIFICADO - PROVA TIPO 1 (BRANCA)", 2025)
        ));

        assertThatThrownBy(() -> service.buscarQuestoesPorProva(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prova não encontrada");
    }

    // ================================================================
    //  verificarResposta
    // ================================================================

    @Test
    @DisplayName("verificarResposta → correta=true quando acerta")
    void verificarResposta_correta() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q = questao(100, prova, 1, false, disciplina(1, "Ética"));
        Alternativa a = alternativa(1001, q, "A", false);
        Alternativa c = alternativa(1003, q, "C", true);
        q.getAlternativas().addAll(List.of(a, c));

        when(questaoRepository.findByIdInComAlternativas(List.of(100)))
                .thenReturn(List.of(q));

        RespostaDetalhadaDTO resposta = service.verificarResposta(100, 1003L);

        assertThat(resposta.questaoId()).isEqualTo(100);
        assertThat(resposta.alternativaEscolhidaId()).isEqualTo(1003L);
        assertThat(resposta.correta()).isTrue();
        assertThat(resposta.alternativas()).hasSize(2);
    }

    @Test
    @DisplayName("verificarResposta → correta=false quando erra")
    void verificarResposta_incorreta() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q = questao(100, prova, 1, false, disciplina(1, "Ética"));
        q.getAlternativas().add(alternativa(1001, q, "A", false));
        q.getAlternativas().add(alternativa(1003, q, "C", true));

        when(questaoRepository.findByIdInComAlternativas(List.of(100)))
                .thenReturn(List.of(q));

        assertThat(service.verificarResposta(100, 1001L).correta()).isFalse();
    }

    @Test
    @DisplayName("verificarResposta → IllegalArgumentException se questão não existe")
    void verificarResposta_questaoInexistente() {
        when(questaoRepository.findByIdInComAlternativas(List.of(999)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.verificarResposta(999, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    @DisplayName("verificarResposta → IllegalArgumentException se alternativa de outra questão")
    void verificarResposta_alternativaDeOutraQuestao() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q = questao(100, prova, 1, false, disciplina(1, "Ética"));
        q.getAlternativas().add(alternativa(1001, q, "A", false));

        when(questaoRepository.findByIdInComAlternativas(List.of(100)))
                .thenReturn(List.of(q));

        assertThatThrownBy(() -> service.verificarResposta(100, 9999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alternativa");
    }

    // ================================================================
    //  buscarPorDisciplinaEAno
    // ================================================================

    @Test
    @DisplayName("buscarPorDisciplinaEAno → repassa filtros para o repository")
    void buscarPorDisciplinaEAno_ambosFiltros() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q = questao(100, prova, 1, false, disciplina(1, "Ética"));
        q.getAlternativas().add(alternativa(1001, q, "A", true));

        when(questaoRepository.findByDisciplinaEAnoComAlternativas(1L, 2025))
                .thenReturn(List.of(q));

        List<QuestaoResumo> resumo = service.buscarPorDisciplinaEAno(1L, 2025);

        assertThat(resumo).hasSize(1);
        assertThat(resumo.get(0).id()).isEqualTo(100);
        verify(questaoRepository).findByDisciplinaEAnoComAlternativas(1L, 2025);
    }

    @Test
    @DisplayName("buscarPorDisciplinaEAno → null nos filtros é repassado como null")
    void buscarPorDisciplinaEAno_semFiltros() {
        when(questaoRepository.findByDisciplinaEAnoComAlternativas(null, null))
                .thenReturn(List.of());

        assertThat(service.buscarPorDisciplinaEAno(null, null)).isEmpty();
    }

    // ================================================================
    //  listarProvas
    // ================================================================

    @Test
    @DisplayName("listarProvas → mapeia para ProvaResumo (sem tipoCor)")
    void listarProvas() {
        when(provaRepository.findAll()).thenReturn(List.of(
                prova(5, "43º EXAME", 2025),
                prova(7, "44º EXAME", 2025)
        ));

        List<ProvaResumo> provas = service.listarProvas();

        assertThat(provas).hasSize(2);
        assertThat(provas.get(0).id()).isEqualTo(5);
        assertThat(provas.get(0).titulo()).isEqualTo("43º EXAME");
        assertThat(provas.get(0).ano()).isEqualTo(2025);
    }

    // ================================================================
    //  listarDisciplinas
    // ================================================================

    @Test
    @DisplayName("listarDisciplinas → mapeia para DisciplinaResumo")
    void listarDisciplinas() {
        when(disciplinaRepository.findAll()).thenReturn(List.of(
                disciplina(1, "Ética e Estatuto da Advocacia"),
                disciplina(2, "Constitucional")
        ));

        List<DisciplinaResumo> lista = service.listarDisciplinas();

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).id()).isEqualTo(1);
        assertThat(lista.get(0).nome()).isEqualTo("Ética e Estatuto da Advocacia");
    }

    // ================================================================
    //  buscarQuestaoPorId
    // ================================================================

    @Test
    @DisplayName("buscarQuestaoPorId → mapeia questão individual")
    void buscarQuestaoPorId_encontrada() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q = questao(100, prova, 1, false, disciplina(1, "Ética"));
        q.getAlternativas().add(alternativa(1001, q, "A", true));

        when(questaoRepository.findByIdInComAlternativas(List.of(100)))
                .thenReturn(List.of(q));

        QuestaoResumo resumo = service.buscarQuestaoPorId(100);

        assertThat(resumo.id()).isEqualTo(100);
        assertThat(resumo.provaId()).isEqualTo(5);
        assertThat(resumo.alternativas()).hasSize(1);
        assertThat(resumo.alternativas().get(0).letra()).isEqualTo("A");
    }

    @Test
    @DisplayName("buscarQuestaoPorId → IllegalArgumentException se não existe")
    void buscarQuestaoPorId_inexistente() {
        when(questaoRepository.findByIdInComAlternativas(List.of(999)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.buscarQuestaoPorId(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrada");
    }

    // ================================================================
    //  buscarGabarito
    // ================================================================

    @Test
    @DisplayName("buscarGabarito → retorna id da alternativa correta de cada questão")
    void buscarGabarito() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q1 = questao(100, prova, 1, false, disciplina(1, "Ética"));
        Questao q2 = questao(101, prova, 2, false, disciplina(1, "Ética"));
        q1.getAlternativas().addAll(List.of(
                alternativa(1001, q1, "A", false),
                alternativa(1002, q1, "B", true)));
        q2.getAlternativas().addAll(List.of(
                alternativa(1003, q2, "A", true),
                alternativa(1004, q2, "B", false)));

        when(questaoRepository.findByIdInComAlternativas(List.of(100, 101)))
                .thenReturn(List.of(q1, q2));

        List<GabaritoResumo> gabaritos = service.buscarGabarito(List.of(100, 101));

        assertThat(gabaritos).hasSize(2);
        assertThat(gabaritos.get(0).questaoId()).isEqualTo(100);
        assertThat(gabaritos.get(0).alternativaCorretaId()).isEqualTo(1002L);
        assertThat(gabaritos.get(1).questaoId()).isEqualTo(101);
        assertThat(gabaritos.get(1).alternativaCorretaId()).isEqualTo(1003L);
    }

    @Test
    @DisplayName("buscarGabarito → alternativaCorretaId null para questão anulada")
    void buscarGabarito_anulada() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao anulada = questao(100, prova, 1, true, disciplina(1, "Ética"));
        anulada.getAlternativas().add(alternativa(1001, anulada, "A", false));
        anulada.getAlternativas().add(alternativa(1002, anulada, "B", false));

        when(questaoRepository.findByIdInComAlternativas(List.of(100)))
                .thenReturn(List.of(anulada));

        List<GabaritoResumo> gabaritos = service.buscarGabarito(List.of(100));

        assertThat(gabaritos).hasSize(1);
        assertThat(gabaritos.get(0).alternativaCorretaId()).isNull();
    }

    // ================================================================
    //  existeAlternativaNaQuestao
    // ================================================================

    @Test
    @DisplayName("existeAlternativaNaQuestao → true quando existe")
    void existeAlternativaNaQuestao_true() {
        when(alternativaRepository.findByIdAndQuestaoId(1001L, 100))
                .thenReturn(Optional.of(alternativa(1001, null, "A", true)));

        assertThat(service.existeAlternativaNaQuestao(100, 1001L)).isTrue();
    }

    @Test
    @DisplayName("existeAlternativaNaQuestao → false quando não existe")
    void existeAlternativaNaQuestao_false() {
        when(alternativaRepository.findByIdAndQuestaoId(9999L, 100))
                .thenReturn(Optional.empty());

        assertThat(service.existeAlternativaNaQuestao(100, 9999L)).isFalse();
    }

    // ================================================================
    //  buscarComGabarito
    // ================================================================

    @Test
    @DisplayName("buscarComGabarito → expõe ehCorreta no DTO")
    void buscarComGabarito() {
        Prova prova = prova(5, "43º EXAME", 2025);
        Questao q = questao(100, prova, 1, false, disciplina(1, "Ética"));
        q.getAlternativas().add(alternativa(1001, q, "A", false));
        q.getAlternativas().add(alternativa(1003, q, "C", true));

        when(questaoRepository.findByIdInComAlternativas(List.of(100)))
                .thenReturn(List.of(q));

        List<QuestaoComGabaritoDTO> lista = service.buscarComGabarito(List.of(100));

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).alternativas()).hasSize(2);
        assertThat(lista.get(0).alternativas())
                .filteredOn(AlternativaComGabarito::ehCorreta)
                .hasSize(1)
                .first()
                .extracting(AlternativaComGabarito::letra)
                .isEqualTo("C");
    }

    // ================================================================
    //  Helpers — criação de entidades via reflection
    // ================================================================

    private Prova prova(int id, String titulo, int ano) {
        Prova p = instanciar(Prova.class);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "titulo", titulo);
        ReflectionTestUtils.setField(p, "ano", ano);
        return p;
    }

    private Questao questao(int id, Prova prova, int numero, boolean anulada, Disciplina disciplina) {
        Questao q = instanciar(Questao.class);
        ReflectionTestUtils.setField(q, "id", id);
        ReflectionTestUtils.setField(q, "prova", prova);
        ReflectionTestUtils.setField(q, "numero", numero);
        ReflectionTestUtils.setField(q, "enunciado", "Enunciado da questão " + id);
        ReflectionTestUtils.setField(q, "anulada", anulada);
        ReflectionTestUtils.setField(q, "disciplina", disciplina);
        return q;
    }

    private Alternativa alternativa(long id, Questao questao, String letra, boolean correta) {
        Alternativa a = instanciar(Alternativa.class);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "questao", questao);
        ReflectionTestUtils.setField(a, "letra", letra);
        ReflectionTestUtils.setField(a, "texto", "Texto " + letra);
        ReflectionTestUtils.setField(a, "comentario", "Comentário " + letra);
        ReflectionTestUtils.setField(a, "ehCorreta", correta);
        return a;
    }

    private Disciplina disciplina(long id, String nome) {
        Disciplina d = instanciar(Disciplina.class);
        ReflectionTestUtils.setField(d, "id", id);
        ReflectionTestUtils.setField(d, "nome", nome);
        return d;
    }

    private <T> T instanciar(Class<T> tipo) {
        try {
            var ctor = tipo.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao instanciar " + tipo.getSimpleName(), e);
        }
    }
}